package com.macro.mall.portal.component;

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
 * 自动确认收货定时器。
 * <p>
 * 扫描已发货且达到 auto_confirm_day 的订单，条件更新为已完成；
 * 条件更新保证与用户手动确认收货并发时只有一方生效。
 */
@Component
public class OrderAutoConfirmTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderAutoConfirmTask.class);
    private static final long LOCK_TTL_SECONDS = 120;

    @Autowired
    private OmsPortalOrderService portalOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${redis.database:mall-swarm}")
    private String redisDatabase;

    @Scheduled(cron = "0 0/5 * * * ?")
    public void autoConfirmOrder() {
        String lockKey = redisDatabase + ":lock:order:auto-confirm";
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            List<Long> orderIds = portalOrderService.listAutoConfirmOrderIds();
            int confirmed = 0;
            for (Long orderId : orderIds) {
                if (portalOrderService.autoConfirmOrder(orderId)) {
                    confirmed++;
                }
            }
            if (!orderIds.isEmpty()) {
                LOGGER.info("order_auto_confirm scanned={} confirmed={} costMs={}",
                        orderIds.size(), confirmed, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            LOGGER.error("order_auto_confirm failed costMs={}",
                    System.currentTimeMillis() - start, e);
        } finally {
            String current = stringRedisTemplate.opsForValue().get(lockKey);
            if (token.equals(current)) {
                stringRedisTemplate.delete(lockKey);
            }
        }
    }
}
