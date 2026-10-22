package com.macro.mall.portal.component;

import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.model.PmsSkuStock;
import com.macro.mall.portal.service.StockLedgerService;
import com.macro.mall.portal.service.StockRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 库存对账：以 MySQL 为事实源，校正 Redis 可售库存。
 * 数据库可售 = stock - lock_stock - 尚未落库的 LOCK 预扣量。
 */
@Component
public class StockReconcileTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockReconcileTask.class);
    private static final long LOCK_TTL_SECONDS = 120;

    @Autowired
    private PmsSkuStockMapper skuStockMapper;
    @Autowired
    private StockLedgerService stockLedgerService;
    @Autowired
    private StockRedisService stockRedisService;

    @Value("${redis.database:mall-swarm}")
    private String redisDatabase;

    @Scheduled(fixedDelay = 600000)
    public void reconcile() {
        String lockKey = redisDatabase + ":lock:stock:reconcile";
        String token = UUID.randomUUID().toString();
        if (!stockRedisService.tryLock(lockKey, token, LOCK_TTL_SECONDS)) {
            return;
        }
        try {
            List<PmsSkuStock> skuStockList = skuStockMapper.selectByExample(null);
            int fixed = 0;
            for (PmsSkuStock skuStock : skuStockList) {
                if (skuStock.getId() == null) {
                    continue;
                }
                int stock = skuStock.getStock() == null ? 0 : skuStock.getStock();
                int lockStock = skuStock.getLockStock() == null ? 0 : skuStock.getLockStock();
                int pending = stockLedgerService.sumPendingLock(skuStock.getId());
                int expected = stock - lockStock - pending;
                if (expected < 0) {
                    LOGGER.warn("stock_reconcile dirty skuId={} stock={} lockStock={} pending={}",
                            skuStock.getId(), stock, lockStock, pending);
                    expected = 0;
                }
                if (stockRedisService.reconcile(skuStock.getId(), expected)) {
                    fixed++;
                }
            }
            LOGGER.info("stock_reconcile done total={} fixed={}", skuStockList.size(), fixed);
        } finally {
            stockRedisService.unlock(lockKey, token);
        }
    }
}
