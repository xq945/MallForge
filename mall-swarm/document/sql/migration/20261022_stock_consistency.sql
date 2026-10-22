-- 库存一致性改造：库存流水表
-- 用途：下单锁定、支付扣减、取消释放的幂等凭证，同时作为 Redis 可售库存对账依据。
-- 注意：本脚本只新增表，不修改历史数据。

CREATE TABLE IF NOT EXISTS `oms_stock_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT       NOT NULL COMMENT '订单ID',
  `order_sn`    VARCHAR(64)  NOT NULL COMMENT '订单号',
  `member_id`   BIGINT       NOT NULL COMMENT '会员ID',
  `sku_id`      BIGINT       NOT NULL COMMENT 'SKU ID',
  `quantity`    INT          NOT NULL COMMENT '数量',
  `event_type`  VARCHAR(16)  NOT NULL COMMENT 'LOCK/PAY/RELEASE/ADJUST',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=PENDING,1=SUCCESS,2=FAILED',
  `biz_key`     VARCHAR(160) NOT NULL COMMENT '幂等键，如 LOCK:orderSn:skuId',
  `remark`      VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_log_biz_key` (`biz_key`),
  KEY `idx_stock_log_order_sn` (`order_sn`),
  KEY `idx_stock_log_type_status` (`event_type`, `status`),
  KEY `idx_stock_log_sku` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存操作流水（幂等与对账）';

-- 检查历史脏数据：lock_stock 为负，或锁定超过总库存。
-- 这些数据不会被本脚本自动修改，需要人工确认后处理。
-- SELECT id, sku_code, stock, lock_stock
-- FROM pms_sku_stock
-- WHERE IFNULL(lock_stock, 0) < 0 OR IFNULL(lock_stock, 0) > stock;
