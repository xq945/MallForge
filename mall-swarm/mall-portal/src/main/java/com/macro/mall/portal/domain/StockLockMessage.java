package com.macro.mall.portal.domain;

import lombok.Data;

import java.util.List;

/**
 * 库存锁定消息：订单创建后异步落库
 */
@Data
public class StockLockMessage {
    private Long orderId;
    private String orderSn;
    private Long memberId;
    private List<StockItem> items;
}
