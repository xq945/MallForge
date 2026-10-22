# 订单超时关闭与自动收货

## 背景

库存一致性改造解决了「下单锁库存」，但订单生命周期的另一半仍有缺口：

- 延迟取消消息在 `generateOrder` 事务提交前发送，订单回滚后仍会发出（取消时查不到订单，属于无效消息）；
- `OrderTimeOutCancelTask` 的 `@Component` 被注释，定时扫描实际不运行；
- `cancelTimeOutOrder` 在一个事务里批量处理所有超时订单，单笔失败会回滚整批；
- 已发货订单没有自动收货任务，`auto_confirm_day` 字段只写不用；
- 用户确认收货使用 `updateByPrimaryKey`，与自动收货并发时可能重复更新。

## 改造

| 问题 | 改造 |
| --- | --- |
| 事务前发延迟消息 | `registerDelayCancelSend` 注册 `afterCommit`，提交后再发；发送异常只记录日志 |
| 定时任务未启用 | `OrderTimeOutCancelTask` 改为 `@Component`，每分钟扫描，Redis 分布式锁防多实例重复 |
| 批量事务 | 新增 `listTimeOutOrderIds`，任务逐单调用带事务的 `cancelOrder`，单笔失败不影响其他订单 |
| 无自动收货 | 新增 `OrderAutoConfirmTask`，每 5 分钟扫描，条件更新 `status 2 -> 3` |
| 确认收货竞态 | `confirmReceiveOrder` 改为条件更新（`status=2`、`confirm_status=0`、`member_id` 匹配） |

超时扫描限制每轮 500 单，避免单次任务执行过久；取消本身通过 `oms_stock_log` 的 RELEASE 唯一键保持幂等。

## 验证（2026-10-22）

1. 通过接口重新创建 6 笔订单，库存 `stock=300, lock_stock=6`，Redis 可售 294。
2. 把其中 3 笔的 `create_time` 改为 3 小时前。
3. 14:13 定时任务执行：`scanned=3 cancelled=3 costMs=105`。
   - 3 笔订单状态变为 4（已关闭）；
   - `lock_stock` 从 6 降到 3；
   - Redis 可售从 294 回到 297；
   - `RELEASE` 流水新增 3 条。
4. 支付剩余 3 笔，设置 `status=2`、`delivery_time=3 天前`、`auto_confirm_day=1`。
5. 14:15 自动收货任务执行：`scanned=7 confirmed=7 costMs=106`，目标 3 笔订单变为 `status=3`、`confirm_status=1`，`receive_time` 写入。
6. 重复执行超时扫描没有新增 `RELEASE` 流水，取消和收货都依赖条件更新保持幂等。

## 已知边界

- RabbitMQ 仍是 per-message TTL + 单队列，若未来存在多个不同超时档位会出现队头阻塞；当前统一 120 分钟，不触发。后续可换延迟插件或按档位拆队列。
- 超时/自动收货任务不发送通知，只做状态流转。
- 自动收货任务每 5 分钟执行一次，最大延迟约 5 分钟。
