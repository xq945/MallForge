package com.macro.mall.portal.service;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OmsStockLog;
import com.macro.mall.portal.domain.StockItem;

import java.util.List;

/**
 * 库存流水与数据库落库服务
 */
public interface StockLedgerService {

    /**
     * 订单创建时写入 LOCK PENDING 流水。
     */
    void recordLockPending(OmsOrder order, List<OmsOrderItem> orderItems);

    /**
     * 把 LOCK 流水落库：db lock_stock += quantity。幂等。
     */
    void applyLock(String orderSn, List<StockItem> items);

    /**
     * 支付：写 PAY 流水并扣减 stock/lock_stock/sale。幂等。
     */
    void applyPay(OmsOrderDetail orderDetail);

    /**
     * 取消：写 RELEASE 流水、释放 db lock_stock 与 Redis 可售。幂等。
     */
    void applyRelease(OmsOrderDetail orderDetail);

    List<OmsStockLog> listPendingLock(int limit);

    int sumPendingLock(Long skuId);
}
