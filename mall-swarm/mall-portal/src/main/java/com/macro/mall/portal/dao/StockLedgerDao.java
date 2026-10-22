package com.macro.mall.portal.dao;

import com.macro.mall.portal.domain.OmsStockLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存流水 Dao
 */
@Mapper
public interface StockLedgerDao {

    String COLUMNS = "id, order_id AS orderId, order_sn AS orderSn, member_id AS memberId, "
            + "sku_id AS skuId, quantity, event_type AS eventType, status, biz_key AS bizKey, "
            + "remark, create_time AS createTime, update_time AS updateTime";

    @Insert("INSERT INTO oms_stock_log "
            + "(order_id, order_sn, member_id, sku_id, quantity, event_type, status, biz_key, remark) "
            + "VALUES (#{orderId}, #{orderSn}, #{memberId}, #{skuId}, #{quantity}, #{eventType}, "
            + "#{status}, #{bizKey}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OmsStockLog log);

    @Select("SELECT " + COLUMNS + " FROM oms_stock_log WHERE biz_key = #{bizKey}")
    OmsStockLog selectByBizKey(@Param("bizKey") String bizKey);

    @Update("UPDATE oms_stock_log SET status = #{status}, remark = #{remark} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status,
                     @Param("remark") String remark);

    @Select("SELECT IFNULL(SUM(quantity), 0) FROM oms_stock_log "
            + "WHERE sku_id = #{skuId} AND event_type = 'LOCK' AND status = 0")
    int sumPendingLock(@Param("skuId") Long skuId);

    @Select("SELECT " + COLUMNS + " FROM oms_stock_log "
            + "WHERE event_type = 'LOCK' AND status = 0 ORDER BY id LIMIT #{limit}")
    List<OmsStockLog> listPendingLock(@Param("limit") int limit);
}
