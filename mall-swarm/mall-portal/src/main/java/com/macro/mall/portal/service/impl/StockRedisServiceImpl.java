package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.dao.StockLedgerDao;
import com.macro.mall.portal.domain.StockItem;
import com.macro.mall.portal.service.StockRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis 可售库存实现：Lua 原子预扣，MySQL 为最终事实源。
 */
@Service
public class StockRedisServiceImpl implements StockRedisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockRedisServiceImpl.class);

    private static final DefaultRedisScript<Long> PRE_DEDUCT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RECONCILE_SCRIPT = new DefaultRedisScript<>();

    static {
        PRE_DEDUCT_SCRIPT.setLocation(new ClassPathResource("lua/stock_pre_deduct.lua"));
        PRE_DEDUCT_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setLocation(new ClassPathResource("lua/stock_release.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
        RECONCILE_SCRIPT.setLocation(new ClassPathResource("lua/stock_reconcile.lua"));
        RECONCILE_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private StockLedgerDao stockLedgerDao;

    @Value("${redis.database:mall-swarm}")
    private String redisDatabase;

    private String stockKey(Long skuId) {
        return redisDatabase + ":stock:avail:" + skuId;
    }

    @Override
    public boolean tryDeduct(List<StockItem> items) {
        List<StockItem> sorted = items.stream()
                .filter(item -> item.getSkuId() != null && item.getQuantity() != null
                        && item.getQuantity() > 0)
                .sorted(Comparator.comparing(StockItem::getSkuId))
                .collect(Collectors.toList());
        List<StockItem> deducted = new ArrayList<>();
        for (StockItem item : sorted) {
            String key = stockKey(item.getSkuId());
            int expected = -1;
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                expected = calculateAvailable(item.getSkuId());
            }
            Long result = stringRedisTemplate.execute(PRE_DEDUCT_SCRIPT, List.of(key),
                    String.valueOf(expected), String.valueOf(item.getQuantity()));
            LOGGER.info("stock_pre_deduct skuId={} quantity={} init={} result={}",
                    item.getSkuId(), item.getQuantity(), expected, result);
            if (result == null || result < 0) {
                release(deducted);
                return false;
            }
            deducted.add(item);
        }
        return true;
    }

    @Override
    public void release(List<StockItem> items) {
        if (items == null) {
            return;
        }
        for (StockItem item : items) {
            if (item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            Long result = stringRedisTemplate.execute(RELEASE_SCRIPT,
                    List.of(stockKey(item.getSkuId())), String.valueOf(item.getQuantity()));
            LOGGER.info("stock_release skuId={} quantity={} result={}",
                    item.getSkuId(), item.getQuantity(), result);
        }
    }

    @Override
    public Integer getAvailable(Long skuId) {
        String value = stringRedisTemplate.opsForValue().get(stockKey(skuId));
        return value == null ? null : Integer.valueOf(value);
    }

    @Override
    public boolean reconcile(Long skuId, int expected) {
        String key = stockKey(skuId);
        String expectedValue = String.valueOf(Math.max(expected, 0));
        Integer current = getAvailable(skuId);
        if (current == null) {
            Boolean created = stringRedisTemplate.opsForValue().setIfAbsent(key, expectedValue);
            if (Boolean.TRUE.equals(created)) {
                LOGGER.info("stock_reconcile skuId={} redis=null db={} fixed=true init=true",
                        skuId, expectedValue);
                return true;
            }
            return false;
        }
        if (current == expected) {
            return false;
        }
        Long fixed = stringRedisTemplate.execute(RECONCILE_SCRIPT, List.of(key),
                String.valueOf(current), expectedValue);
        LOGGER.info("stock_reconcile skuId={} redis={} db={} diff={} fixed={}",
                skuId, current, expected, current - expected, fixed);
        return fixed != null && fixed == 1L;
    }

    @Override
    public boolean tryLock(String key, String token, long ttlSeconds) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, token, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock(String key, String token) {
        String current = stringRedisTemplate.opsForValue().get(key);
        if (token.equals(current)) {
            stringRedisTemplate.delete(key);
        }
    }

    /**
     * 数据库可售 = stock - lock_stock - 尚未落库的预扣量。
     */
    private int calculateAvailable(Long skuId) {
        PmsSkuStock skuStock = skuStockMapper.selectByPrimaryKey(skuId);
        if (skuStock == null) {
            return -1;
        }
        int stock = skuStock.getStock() == null ? 0 : skuStock.getStock();
        int lockStock = skuStock.getLockStock() == null ? 0 : skuStock.getLockStock();
        int pending = stockLedgerDao.sumPendingLock(skuId);
        return stock - lockStock - pending;
    }
}
