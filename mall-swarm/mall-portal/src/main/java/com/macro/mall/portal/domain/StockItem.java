package com.macro.mall.portal.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存操作项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {
    private Long skuId;
    private Integer quantity;
}
