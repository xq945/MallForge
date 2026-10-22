package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.domain.StockLockMessage;
import com.macro.mall.portal.service.StockLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 库存锁定消息消费者：把 Redis 预扣结果异步落到 pms_sku_stock.lock_stock。
 */
@Component
@RabbitListener(queues = "mall.stock.lock")
public class StockLockReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockLockReceiver.class);

    @Autowired
    private StockLedgerService stockLedgerService;
    @Autowired
    private ObjectMapper objectMapper;

    @RabbitHandler
    public void handle(String payload) {
        try {
            StockLockMessage message = objectMapper.readValue(payload, StockLockMessage.class);
            stockLedgerService.applyLock(message.getOrderSn(), message.getItems());
            LOGGER.info("stock_mq_consume orderSn={} consumeTs={} result=success",
                    message.getOrderSn(), System.currentTimeMillis());
        } catch (Exception e) {
            // 不抛出，避免消息无限 requeue；LOCK 流水保持 PENDING，由 StockLockRetryTask 补发。
            LOGGER.error("stock_mq_consume failed payload={}", payload, e);
        }
    }
}
