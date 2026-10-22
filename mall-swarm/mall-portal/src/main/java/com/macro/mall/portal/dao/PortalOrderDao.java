package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 前台订单自定义Dao
 * Created by macro on 2018/9/4.
 */
public interface PortalOrderDao {
    /**
     * 获取订单及下单商品详情
     */
    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    /**
     * 修改 pms_sku_stock表的锁定库存及真实库存
     */
    int updateSkuStock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 获取超时订单
     * @param minute 超时时间（分）
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute);

    /**
     * 批量修改订单状态
     */
    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 解除取消订单的库存锁定
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 条件锁定库存：仅在可售库存足够时加锁，返回 0 表示库存不足。
     */
    int lockSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 条件支付扣减：同时扣减 stock 与 lock_stock，并累加销量。
     */
    int paySkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 条件释放锁定库存。
     */
    int releaseSkuStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    /**
     * 仅当订单仍为待付款时更新为已支付，返回 0 表示已被处理或已取消。
     */
    int updateOrderPaySuccess(@Param("orderId") Long orderId, @Param("payType") Integer payType);

    /**
     * 仅当订单仍为待付款时取消，返回 0 表示已被处理。
     */
    int cancelOrderIfPending(@Param("orderId") Long orderId);

    /**
     * 查询超时未支付订单 ID，供定时任务逐单取消。
     */
    List<Long> getTimeOutOrderIds(@Param("minute") Integer minute);

    /**
     * 查询已发货且超过自动收货天数的订单 ID。
     */
    List<Long> getAutoConfirmOrderIds();

    /**
     * 用户确认收货：条件更新，返回 0 表示状态已变更。
     */
    int confirmReceiveOrder(@Param("orderId") Long orderId, @Param("memberId") Long memberId);

    /**
     * 自动确认收货：条件更新，返回 0 表示状态已变更。
     */
    int autoConfirmOrder(@Param("orderId") Long orderId);

}
