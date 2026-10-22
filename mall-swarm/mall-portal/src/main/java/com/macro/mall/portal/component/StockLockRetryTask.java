package com.macro.mall.portal.component;

import com.macro.mall.portal.domain.OmsStockLog;
import com.macro.mall.portal.domain.StockItem;
import com.macro.mall.portal.domain.StockLockMessage;
import com.macro.mall.portal.service.StockLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 补发未落库的 LOCK 消息，处理 MQ 发送失败或消费者宕机。
 */
@Component
public class StockLockRetryTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockLockRetryTask.class);

    @Autowired
    private StockLedgerService stockLedgerService;
    @Autowired
    private StockLockSender stockLockSender;

    @Scheduled(fixedDelay = 30000)
    public void retryPendingLock() {
        List<OmsStockLog> pending = stockLedgerService.listPendingLock(100);
        if (pending.isEmpty()) {
            return;
        }
        Map<Long, List<OmsStockLog>> byOrder = new LinkedHashMap<>();
        for (OmsStockLog log : pending) {
            byOrder.computeIfAbsent(log.getOrderId(), key -> new ArrayList<>()).add(log);
        }
        for (Map.Entry<Long, List<OmsStockLog>> entry : byOrder.entrySet()) {
            List<OmsStockLog> logs = entry.getValue();
            StockLockMessage message = new StockLockMessage();
            message.setOrderId(entry.getKey());
            message.setOrderSn(logs.get(0).getOrderSn());
            message.setMemberId(logs.get(0).getMemberId());
            List<StockItem> items = new ArrayList<>();
            for (OmsStockLog log : logs) {
                items.add(new StockItem(log.getSkuId(), log.getQuantity()));
            }
            message.setItems(items);
            stockLockSender.send(message);
            LOGGER.info("stock_lock_retry orderSn={} pending={}", message.getOrderSn(), logs.size());
        }
    }
}
