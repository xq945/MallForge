package com.macro.mall.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.portal.domain.QueueEnum;
import com.macro.mall.portal.domain.StockLockMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 库存锁定消息发送者
 */
@Component
public class StockLockSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockLockSender.class);

    @Autowired
    private AmqpTemplate amqpTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    public void send(StockLockMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            amqpTemplate.convertAndSend(
                    QueueEnum.QUEUE_STOCK_LOCK.getExchange(),
                    QueueEnum.QUEUE_STOCK_LOCK.getRouteKey(),
                    payload);
            LOGGER.info("stock_mq_send orderSn={} orderId={} sendTs={}",
                    message.getOrderSn(), message.getOrderId(), System.currentTimeMillis());
        } catch (Exception e) {
            // 发送失败不阻断下单：LOCK 流水保持 PENDING，由重试任务补发。
            LOGGER.error("stock_mq_send failed orderSn={} orderId={}",
                    message.getOrderSn(), message.getOrderId(), e);
        }
    }
}
