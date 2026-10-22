package com.macro.mall.portal.component;

import com.macro.mall.mapper.OmsOrderSettingMapper;
import com.macro.mall.model.OmsOrderSetting;
import com.macro.mall.portal.service.OmsPortalOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 订单超时取消定时器。
 * <p>
 * 延迟消息可能因 MQ 发送失败或队头阻塞而失效，本任务每分钟扫描一次超时未支付订单，
 * 逐单调用带事务的 cancelOrder，保证取消与库存释放至少发生一次（cancelOrder 本身幂等）。
 */
@Component
public class OrderTimeOutCancelTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderTimeOutCancelTask.class);
    private static final long LOCK_TTL_SECONDS = 120;

    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private OmsOrderSettingMapper orderSettingMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${redis.database:mall-swarm}")
    private String redisDatabase;

    /**
     * 每分钟扫描一次，超时时间取 oms_order_setting.normal_order_overtime。
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void cancelTimeOutOrder() {
        String lockKey = redisDatabase + ":lock:order:timeout";
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            OmsOrderSetting setting = orderSettingMapper.selectByPrimaryKey(1L);
            if (setting == null || setting.getNormalOrderOvertime() == null) {
                return;
            }
            List<Long> orderIds = portalOrderService.listTimeOutOrderIds(setting.getNormalOrderOvertime());
            int cancelled = 0;
            for (Long orderId : orderIds) {
                // 通过 Spring 代理逐单调用，每单独立事务，单笔失败不影响其他订单
                portalOrderService.cancelOrder(orderId);
                cancelled++;
            }
            if (!orderIds.isEmpty()) {
                LOGGER.info("order_timeout_cancel scanned={} cancelled={} costMs={}",
                        orderIds.size(), cancelled, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            LOGGER.error("order_timeout_cancel failed costMs={}",
                    System.currentTimeMillis() - start, e);
        } finally {
            String current = stringRedisTemplate.opsForValue().get(lockKey);
            if (token.equals(current)) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }
}
