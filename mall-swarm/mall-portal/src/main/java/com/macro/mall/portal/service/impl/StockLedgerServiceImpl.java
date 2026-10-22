package com.macro.mall.portal.service.impl;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.dao.PortalOrderDao;
import com.macro.mall.portal.dao.StockLedgerDao;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OmsStockLog;
import com.macro.mall.portal.domain.StockItem;
import com.macro.mall.portal.service.StockLedgerService;
import com.macro.mall.portal.service.StockRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存流水与数据库落库实现。
 *
 * 流水 biz_key 是幂等键：LOCK/PAY/RELEASE + 订单号 + SKU。
 * 数据库更新全部使用条件 SQL，影响行数为 0 表示状态已经被其他请求改变或库存不足。
 */
@Service
public class StockLedgerServiceImpl implements StockLedgerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockLedgerServiceImpl.class);

    @Autowired
    private StockLedgerDao stockLedgerDao;
    @Autowired
    private PortalOrderDao portalOrderDao;
    @Autowired
    private StockRedisService stockRedisService;

    @Override
    @Transactional
    public void recordLockPending(OmsOrder order, List<OmsOrderItem> orderItems) {
        for (OmsOrderItem item : orderItems) {
            OmsStockLog log = buildLog(order.getId(), order.getOrderSn(), order.getMemberId(),
                    item.getProductSkuId(), item.getProductQuantity(), OmsStockLog.EVENT_LOCK);
            insertIgnoreDuplicate(log);
        }
    }

    @Override
    @Transactional
    public void applyLock(String orderSn, List<StockItem> items) {
        List<StockItem> sorted = sort(items);
        for (StockItem item : sorted) {
            String bizKey = bizKey(OmsStockLog.EVENT_LOCK, orderSn, item.getSkuId());
            OmsStockLog log = stockLedgerDao.selectByBizKey(bizKey);
            if (log == null) {
                LOGGER.warn("stock_persist lock ledger missing orderSn={} skuId={}", orderSn, item.getSkuId());
                continue;
            }
            if (log.getStatus() != null && log.getStatus() == OmsStockLog.STATUS_SUCCESS) {
                continue;
            }
            int affected = portalOrderDao.lockSkuStock(item.getSkuId(), item.getQuantity());
            if (affected == 0) {
                throw new IllegalStateException("库存锁定失败，库存不足 orderSn=" + orderSn
                        + " skuId=" + item.getSkuId());
            }
            stockLedgerDao.updateStatus(log.getId(), OmsStockLog.STATUS_SUCCESS, "db locked");
            LOGGER.info("stock_persist orderSn={} skuId={} quantity={} result=success",
                    orderSn, item.getSkuId(), item.getQuantity());
        }
    }

    @Override
    @Transactional
    public void applyPay(OmsOrderDetail orderDetail) {
        List<StockItem> items = toStockItems(orderDetail.getOrderItemList());
        for (StockItem item : sort(items)) {
            String bizKey = bizKey(OmsStockLog.EVENT_PAY, orderDetail.getOrderSn(), item.getSkuId());
            OmsStockLog log = buildLog(orderDetail.getId(), orderDetail.getOrderSn(),
                    orderDetail.getMemberId(), item.getSkuId(), item.getQuantity(), OmsStockLog.EVENT_PAY);
            try {
                stockLedgerDao.insert(log);
            } catch (DuplicateKeyException e) {
                LOGGER.info("stock_pay duplicate orderSn={} skuId={}", orderDetail.getOrderSn(), item.getSkuId());
                continue;
            }
            int affected = portalOrderDao.paySkuStock(item.getSkuId(), item.getQuantity());
            if (affected == 0) {
                throw new IllegalStateException("支付扣减库存失败 orderSn=" + orderDetail.getOrderSn()
                        + " skuId=" + item.getSkuId());
            }
            stockLedgerDao.updateStatus(log.getId(), OmsStockLog.STATUS_SUCCESS, "paid");
            LOGGER.info("stock_pay orderSn={} skuId={} quantity={} result=success",
                    orderDetail.getOrderSn(), item.getSkuId(), item.getQuantity());
        }
    }

    @Override
    @Transactional
    public void applyRelease(OmsOrderDetail orderDetail) {
        List<StockItem> items = sort(toStockItems(orderDetail.getOrderItemList()));
        // 先确保 LOCK 已落库，否则取消时无锁可释放
        applyLock(orderDetail.getOrderSn(), items);
        for (StockItem item : items) {
            String bizKey = bizKey(OmsStockLog.EVENT_RELEASE, orderDetail.getOrderSn(), item.getSkuId());
            OmsStockLog log = buildLog(orderDetail.getId(), orderDetail.getOrderSn(),
                    orderDetail.getMemberId(), item.getSkuId(), item.getQuantity(), OmsStockLog.EVENT_RELEASE);
            try {
                stockLedgerDao.insert(log);
            } catch (DuplicateKeyException e) {
                LOGGER.info("stock_release duplicate orderSn={} skuId={}",
                        orderDetail.getOrderSn(), item.getSkuId());
                continue;
            }
            int affected = portalOrderDao.releaseSkuStock(item.getSkuId(), item.getQuantity());
            if (affected == 0) {
                LOGGER.warn("stock_release no lock to release orderSn={} skuId={}",
                        orderDetail.getOrderSn(), item.getSkuId());
            }
            stockRedisService.release(List.of(item));
            stockLedgerDao.updateStatus(log.getId(), OmsStockLog.STATUS_SUCCESS, "released");
            LOGGER.info("stock_release orderSn={} skuId={} quantity={} result=success",
                    orderDetail.getOrderSn(), item.getSkuId(), item.getQuantity());
        }
    }

    @Override
    public List<OmsStockLog> listPendingLock(int limit) {
        return stockLedgerDao.listPendingLock(limit);
    }

    @Override
    public int sumPendingLock(Long skuId) {
        return stockLedgerDao.sumPendingLock(skuId);
    }

    private void insertIgnoreDuplicate(OmsStockLog log) {
        try {
            stockLedgerDao.insert(log);
        } catch (DuplicateKeyException e) {
            LOGGER.info("stock_lock duplicate orderSn={} skuId={}", log.getOrderSn(), log.getSkuId());
        }
    }

    private OmsStockLog buildLog(Long orderId, String orderSn, Long memberId,
                                 Long skuId, Integer quantity, String eventType) {
        OmsStockLog log = new OmsStockLog();
        log.setOrderId(orderId);
        log.setOrderSn(orderSn);
        log.setMemberId(memberId);
        log.setSkuId(skuId);
        log.setQuantity(quantity);
        log.setEventType(eventType);
        log.setStatus(OmsStockLog.STATUS_PENDING);
        log.setBizKey(bizKey(eventType, orderSn, skuId));
        return log;
    }

    private String bizKey(String eventType, String orderSn, Long skuId) {
        return eventType + ":" + orderSn + ":" + skuId;
    }

    private List<StockItem> toStockItems(List<OmsOrderItem> orderItems) {
        List<StockItem> items = new ArrayList<>();
        if (orderItems == null) {
            return items;
        }
        for (OmsOrderItem item : orderItems) {
            items.add(new StockItem(item.getProductSkuId(), item.getProductQuantity()));
        }
        return items;
    }

    private List<StockItem> sort(List<StockItem> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream()
                .filter(item -> item.getSkuId() != null && item.getQuantity() != null
                        && item.getQuantity() > 0)
                .sorted(Comparator.comparing(StockItem::getSkuId))
                .collect(Collectors.toList());
    }
}
