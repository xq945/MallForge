package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.StockItem;

import java.util.List;

/**
 * Redis 可售库存服务
 */
public interface StockRedisService {

    /**
     * 原子预扣，任一 SKU 不足则回滚本次已扣部分。
     */
    boolean tryDeduct(List<StockItem> items);

    /**
     * 释放预扣。
     */
    void release(List<StockItem> items);

    Integer getAvailable(Long skuId);

    /**
     * 按数据库可售库存校正 Redis，返回是否发生修正。
     */
    boolean reconcile(Long skuId, int expected);

    boolean tryLock(String key, String token, long ttlSeconds);

    void unlock(String key, String token);
}
