package com.macro.mall.portal.domain;

import lombok.Data;

import java.util.Date;

/**
 * 库存操作流水
 */
@Data
public class OmsStockLog {
    public static final String EVENT_LOCK = "LOCK";
    public static final String EVENT_PAY = "PAY";
    public static final String EVENT_RELEASE = "RELEASE";
    public static final String EVENT_ADJUST = "ADJUST";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;

    private Long id;
    private Long orderId;
    private String orderSn;
    private Long memberId;
    private Long skuId;
    private Integer quantity;
    private String eventType;
    private Integer status;
    private String bizKey;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
