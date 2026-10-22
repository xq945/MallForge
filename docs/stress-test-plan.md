# MallForge 库存一致性压测方案

> 目的：为「下单库存一致性」改造提供可复现的压测方法，产出能直接回填简历的真实数据。本文只定义方法与目标，不预填任何结果数字。

## 1. 压测目标

1. 验证改造后高并发下单**不超卖**、库存最终一致。
2. 量化改造前后的吞吐与延迟差异。
3. 记录足够细的过程日志，面试被追问「怎么测的」时能拿出证据。

## 2. 被测链路

| 步骤 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 登录 | POST | `/sso/login` | form 参数 `username`、`password`，返回 `token` 与 `tokenHead` |
| 加购 | POST | `/cart/add` | JSON 请求体，字段见 `OmsCartItem` |
| 下单 | POST | `/order/generateOrder` | JSON：`memberReceiveAddressId`、`cartIds` |
| 支付回调（可选） | POST | `/order/paySuccess` | 验证支付后扣减真实库存 |

鉴权头：`Authorization: Bearer <token>`（`sa-token.token-prefix=Bearer`）。

当前实现的库存锁定在 `OmsPortalOrderServiceImpl#lockStock`：先 `selectByPrimaryKey`，再 `setLockStock(lockStock + quantity)`，最后 `updateByPrimaryKeySelective`，属于读-改-写，高并发下存在丢失更新风险。压测基线跑通后，再用同一脚本对比改造版本。

## 3. 环境

| 组件 | 地址/端口 | 备注 |
| --- | --- | --- |
| mall-portal | `localhost:8085` | 直连服务，避免网关成为变量 |
| mall-gateway | `localhost:8201` | 可选，验证全链路时使用 |
| MySQL | `localhost:3307` | docker-compose 映射 |
| Redis | `localhost:6380` | 无密码 |
| Nacos | `localhost:8848` | 配置中心 |
| RabbitMQ | `localhost:5672` | 管理台 15672 |

压测机与被压服务最好分开。如果只能同机，必须在结果里注明「JMeter 与服务同机，CPU 存在竞争」，并在压测时关闭 IDE、浏览器等占用。

记录每次压测的版本信息：

| 项目 | 值 |
| --- | --- |
| git commit | 待填 |
| 机器配置（CPU/内存） | 待填 |
| JDK 版本 | 待填 |
| JMeter 版本 | 待填 |
| 是否同机 | 待填 |

## 4. 测试数据

固定一个 SKU，便于核对：

- SKU：`skuId = 1`（按实际库调整）
- 初始库存：`stock = 1000`，`lock_stock = 0`
- 价格：1 元，避免金额计算影响
- 会员：每个并发用户一个独立账号，互不共享购物车

### 4.1 账号与地址

需要 `N` 个会员账号，每个账号一条收货地址。账号可以用 SQL 批量插入，密码使用项目现有的 BCrypt 加密方式（与 `ums_member` 中已有账号一致），不再走需要验证码的注册接口。

```sql
-- 示例，字段按实际表结构调整
INSERT INTO ums_member (username, password, phone, status, create_time)
VALUES ('loadtest001', '<bcrypt-hash>', '13900000001', 1, NOW());
```

地址同理，`member_id` 与账号一一对应：

```sql
INSERT INTO ums_member_receive_address
  (member_id, name, phone_number, default_status, province, city, region, detail_address)
VALUES (?, '压测用户', '13900000001', 1, '江西省', '南昌市', '青山湖区', '测试地址');
```

### 4.2 购物车

两种做法，二选一：

- **预置购物车**：压测前直接写 `oms_cart_item`，每人一条，`quantity = 1`。适合「每人只下一单」的一致性场景。
- **脚本加购**：JMeter 中先调 `/cart/add`，再调 `/cart/list` 取 `cartId`。更接近真实用户，但多一个变量，适合吞吐场景。

### 4.3 每轮重置

每档并发前重置，保证各轮起点一致：

```sql
UPDATE pms_sku_stock SET stock = 1000, lock_stock = 0 WHERE id = 1;
DELETE FROM oms_order WHERE member_id BETWEEN ? AND ?;
DELETE FROM oms_cart_item WHERE member_id BETWEEN ? AND ?;
-- 重新插入预置购物车
```

Redis 侧的库存 key 也必须在每轮前删除，例如：

```bash
redis-cli -p 6380 DEL stock:sku:1
```

## 5. 压测场景

### S1：改造前基线

用当前 `lockStock` 实现跑一遍，记录是否出现超卖、丢失更新、吞吐与 P99。**基线数据是「改造前」的对比依据，必须保留原始 jtl 和报告。**

### S2：改造后

同一脚本、同一数据、同一机器配置，跑改造版本。

### 并发梯度

| 档位 | 线程数 | Ramp-up | 每轮时长 | 轮数 |
| --- | --- | --- | --- | --- |
| 1 | 50 | 10s | 直至线程结束 | 3 |
| 2 | 100 | 20s | 直至线程结束 | 3 |
| 3 | 200 | 30s | 直至线程结束 | 3 |
| 4 | 500 | 30s | 直至线程结束 | 3 |
| 5 | 1000 | 60s | 直至线程结束 | 3 |

每档 3 轮取中位数，避免单次抖动。库存只有 1000 件，超过 1000 并发后大部分请求应返回「库存不足」，这是预期行为，不是错误。

### 两种线程模型

- **一致性模型（主）**：线程数 = 用户数，Loop Count = 1，每人只下一单。验证「成功订单数 ≤ 初始库存、超卖 0」。
- **吞吐模型（辅）**：固定 50 线程循环执行「加购 → 下单」，持续 5 分钟。用于测 QPS 与 P99，需要每轮重新加购。

## 6. JMeter 脚本结构

```
mall-stock-consistency.jmx
├── Test Plan
│   ├── User Defined Variables
│   │   ├── HOST = localhost
│   │   ├── PORT = 8085
│   │   └── SKU_ID = 1
│   ├── HTTP Request Defaults
│   ├── HTTP Header Manager
│   │   └── Content-Type: application/json
│   ├── CSV Data Set Config
│   │   ├── Filename: accounts.csv
│   │   ├── Variable Names: token,addressId,cartId,memberId
│   │   └── Recycle on EOF: false
│   ├── Thread Group（并发数由 -Jthreads 控制）
│   │   ├── HTTP Header Manager
│   │   │   └── Authorization: Bearer ${token}
│   │   ├── Transaction Controller「下单」
│   │   │   ├── HTTP Request：POST /order/generateOrder
│   │   │   └── Response Assertion：$.code == 200
│   │   ├── JSON Extractor：$.data.orderSn → orderSn
│   │   └── Aggregate Report / Summary Report
│   └── View Results Tree（调试时开，正式压测关）
```

### 请求体

```json
{
  "memberReceiveAddressId": ${addressId},
  "cartIds": [${cartId}]
}
```

### 关键配置

| 配置项 | 值 |
| --- | --- |
| 线程数 | `${__P(threads,500)}` |
| Ramp-up | `${__P(rampup,30)}` |
| Loop Count | 1（一致性） / 持续 300s（吞吐） |
| 超时 | connect 2s / response 10s |
| 断言 | HTTP 200 且 JSON `$.code == 200` |
| 日志字段 | 保存 `timeStamp,elapsed,label,responseCode,success,threadName,Latency,Connect` |
| 监听器 | 正式压测只留 Aggregate Report + 写 jtl |

### 命令行执行

```bash
jmeter -n -t mall-stock-consistency.jmx \
  -Jthreads=500 -Jrampup=30 \
  -l result/500-1.jtl \
  -e -o report/500-1
```

### 账号准备脚本（Python）

```python
# scripts/prepare_loadtest.py（示意）
# 1. 读取账号列表
# 2. POST /sso/login 获取 token
# 3. POST /cart/add 加入 SKU
# 4. GET /cart/list 取 cartId
# 5. 写入 accounts.csv: token,addressId,cartId,memberId
```

## 7. 日志埋点

改造代码里至少记录以下事件，字段用 `key=value` 便于 grep 和统计：

### 预扣

```text
stock_pre_deduct orderSn={} memberId={} skuId={} quantity={} before={} after={} result={} costMs={}
```

### MQ 发送

```text
stock_mq_send orderSn={} messageId={} skuId={} quantity={} sendTs={} result={}
```

### MQ 消费落库

```text
stock_persist orderSn={} messageId={} skuId={} quantity={} sendTs={} consumeTs={} result={} retry={}
```

### 对账

```text
stock_reconcile skuId={} redisStock={} dbStock={} diff={} fixed={}
```

### 建议的 Micrometer 指标

| 指标 | 类型 | 含义 |
| --- | --- | --- |
| `order_success_total` | Counter | 下单成功数 |
| `order_stock_short_total` | Counter | 库存不足数 |
| `order_other_fail_total` | Counter | 其他失败数 |
| `order_latency_seconds` | Timer | 下单接口耗时 |
| `stock_remain` | Gauge | 当前可用库存 |
| `stock_persist_lag_seconds` | Timer | 发送到落库的延迟 |

## 8. 结果采集与判定

### 8.1 指标口径

| 指标 | 口径 | 采集方式 | 目标 |
| --- | --- | --- | --- |
| 成功订单数 | `oms_order` 中本轮新增订单数 | SQL | 待测 |
| 超卖单数 | 成功订单数 − 初始库存（仅当 >0 计） | SQL | 0 |
| 库存余量 | `stock - lock_stock` | SQL | 待测 |
| Redis 余量 | `GET stock:sku:1` | redis-cli | 与 DB 一致 |
| 下单 QPS | 成功请求数 / 压测时长 | JMeter | 待测 |
| P50/P95/P99 | 下单接口响应时间分位 | JMeter 报告 | 待测 |
| 库存不足率 | message 含「库存不足」/ 总请求 | jtl + 日志 | 待测 |
| 错误率（非库存不足） | 其他失败 / 总请求 | jtl | < 1% |
| 回写延迟 P99 | `consumeTs - sendTs` | 日志 | 待测 |
| 消息积压 | RabbitMQ 队列 messages 峰值 | `rabbitmqctl list_queues` | 待测 |
| 对账差异 | 最近一次对账 `diff` | 日志 | 0 |
| 资源占用 | CPU、内存、MySQL 连接数 | top / Prometheus | 记录 |

### 8.2 一致性校验 SQL

```sql
-- 成功订单数
SELECT COUNT(*) FROM oms_order WHERE member_id BETWEEN ? AND ?;

-- 库存状态
SELECT stock, lock_stock, stock - lock_stock AS available
FROM pms_sku_stock WHERE id = 1;

-- 订单明细与库存扣减总量核对
SELECT SUM(product_quantity) FROM oms_order_item
WHERE order_id IN (SELECT id FROM oms_order WHERE member_id BETWEEN ? AND ?);
```

### 8.3 判定标准

1. 所有档位成功订单数 ≤ 1000，超卖单数 = 0。
2. 最终 `stock - lock_stock ≥ 0`，Redis 余量与 DB 余量一致（允许对账窗口内的短暂差异，对账后必须为 0）。
3. 非库存不足类错误率 < 1%。
4. 改造后 P99 不高于同档基线的 1.5 倍；QPS 不低于基线。
5. 对账任务无未修复差异。

## 9. 结果记录模板

| 场景 | 并发 | 初始库存 | 成功订单 | 超卖 | QPS | P50 | P95 | P99 | 错误率 | 回写 P99 | 对账差异 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 改造前 | 50 | 1000 | | | | | | | | — | — |
| 改造前 | 500 | 1000 | | | | | | | | — | — |
| 改造后 | 50 | 1000 | | | | | | | | | |
| 改造后 | 500 | 1000 | | | | | | | | | |
| 改造后 | 1000 | 1000 | | | | | | | | | |

## 10. 简历回填

压测完成后，只使用实测数据。可以写成：

> 在 1000 件库存、500 并发用户下完成压测：成功下单 1000 单、超卖 0 单；下单接口 P99 __ ms，库存回写 P99 __ ms，对账差异 0。

如果基线没有跑，就不要写「从 X 降到 Y」，只能写改造后的绝对值。

**不要写**：未实测的百分比、未对比的「提升 N 倍」、没有口径的 QPS。

## 11. 注意事项

- 只压本地/测试环境，不要对生产或公网环境压测。
- 压测数据与业务数据隔离，使用 `loadtest` 前缀账号，结束后清理。
- 每档开始前重置库存、订单、购物车和 Redis key。
- 压测时监控 MySQL CPU、连接数、RabbitMQ 队列长度，避免中间件先成为瓶颈导致结论失真。
- JMeter 与被压服务同机时，在报告里注明资源竞争。
- 保留每轮 `.jtl` 和 HTML 报告，简历数据要能追溯到原始文件。

## 12. 本轮实测记录（2026-10-22）

### 12.1 环境

| 项目 | 值 |
| --- | --- |
| 应用 | mall-portal 单实例，JDK 21，Tomcat max-threads=500，Druid max-active=50 |
| 中间件 | MySQL 3307、Redis 6380、RabbitMQ 5672，均为本机 Docker |
| 压测客户端 | Python `requests` + 500 线程，与服务同机 |
| 数据 | SKU 99，初始库存 300；500 个会员各预置 1 条购物车，每人限购 1 件 |
| 请求 | `POST /order/generateOrder`，每个会员独立 token、地址、购物车和 requestId |

### 12.2 结果

| 指标 | 结果 |
| --- | --- |
| 总请求 | 500 |
| 成功下单 | 300 |
| 库存不足被拦截 | 200 |
| HTTP 错误 | 0 |
| 超卖订单 | 0 |
| 最终 stock / lock_stock / 可售 | 300 / 300 / 0 |
| Redis 可售库存 | 0 |
| LOCK 流水 SUCCESS | 300（本轮） |
| LOCK 流水 PENDING | 0 |
| 成功请求 P50 | 2066 ms |
| 成功请求 P95 | 2969 ms |
| 成功请求 P99 | 3002 ms |

功能验证：

- 同一订单重复支付：第二次返回 `data=0`，库存只扣一次。
- 同一订单重复取消：只产生一条 `RELEASE` 流水，`lock_stock` 不会重复释放。
- 压测后 `oms_order` 订单数 = 初始库存 = 300，`order_item` 数量合计 300。

### 12.3 说明

- 客户端与服务同机，延迟包含资源竞争，**不能作为线上性能承诺**；可用于改造前后的同条件对比。
- 压测前已把 4 个历史脏数据 SKU 的负数 `lock_stock` 归零，本轮测试 SKU 99 的基线为 `stock=300, lock_stock=0`。
- 本轮没有跑改造前基线，因此不写「从 X 降到 Y」，只写改造后的绝对值。
