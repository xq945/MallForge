# MallForge

> MallForge 是基于 [macrozheng/mall-swarm](https://github.com/macrozheng/mall-swarm) 系列上游项目整合的二次开发版本，代码版权归原作者所有，遵循 Apache License 2.0。上游版本信息见下方「版本基线」。

MallForge 是一个电商系统全家桶，把后端微服务与两套前端整合在同一个仓库里，便于统一阅读、构建和联调。

## 仓库结构

| 目录 | 说明 | 技术栈 |
| --- | --- | --- |
| [`mall-swarm/`](mall-swarm/) | 后端微服务 | Java 17 · Spring Boot 3.5.14 · Spring Cloud 2025.0.2 · Spring Cloud Alibaba 2025.0.0.0 · Nacos · Sa-Token |
| [`mall-admin-web/`](mall-admin-web/) | 后台管理前端 | Vue 3 · Vite · Element Plus · TypeScript |
| [`mall-app-web/`](mall-app-web/) | 移动端商城前端 | Vue 3 · uni-app（H5 / 小程序 / App） |
| [`docker-compose.yml`](docker-compose.yml) | 本地中间件编排 | MySQL · Redis · MongoDB · Elasticsearch · RabbitMQ · Nacos · MinIO |
| [`scripts/`](scripts/) | 本地环境辅助脚本 | Python 3 / Bash |

根目录另有 [LOCAL-DEV.md](LOCAL-DEV.md)（首次搭建）与 [STARTUP.md](STARTUP.md)（日常启停与排错）。

## 后端模块

| 模块 | 职责 | 端口 |
| --- | --- | --- |
| `mall-gateway` | API 网关，统一入口与鉴权 | 8201 |
| `mall-auth` | 认证中心（Sa-Token，Redis 存储 + JWT） | 8401 |
| `mall-admin` | 后台管理服务 | 8080 |
| `mall-portal` | 前台商城服务 | 8085 |
| `mall-search` | 搜索服务（Elasticsearch，依赖 IK 分词） | 8081 |
| `mall-monitor` | 监控中心（Spring Boot Admin） | 8101 |

此外还有 `mall-common`（公共模块）、`mall-mbg`（代码生成）、`mall-demo`（示例）。

## 版本基线

本仓库为 [macrozheng](https://github.com/macrozheng) 上游代码的整合快照，对应以下 commit：

| 子项目 | 上游仓库 | commit |
| --- | --- | --- |
| mall-swarm | [macrozheng/mall-swarm](https://github.com/macrozheng/mall-swarm) | `04c442f` |
| mall-admin-web | [macrozheng/mall-admin-web](https://github.com/macrozheng/mall-admin-web) | `81fc17e` |
| mall-app-web | [macrozheng/mall-app-web](https://github.com/macrozheng/mall-app-web) | `b1eb3cd` |

如需获取上游更新，请直接对照上表对应的仓库。

## 快速开始

> 首次搭建的完整步骤（拉镜像、建库、导入配置）见 [LOCAL-DEV.md](LOCAL-DEV.md)，启动后的排错见 [STARTUP.md](STARTUP.md)。

### 1. 启动中间件

```bash
docker compose up -d
```

首次启动还需两步（之后不必重复）：

```bash
# ES 安装 IK 分词插件，否则 mall-search 启动即退出
bash scripts/setup-es-ik.sh
docker compose up -d elasticsearch

# 把 mall-swarm/config 下的 dev 配置导入 Nacos
python scripts/import-nacos-config.py
```

### 2. 构建并启动后端

```bash
cd mall-swarm
mvn clean package -DskipTests -Ddocker.skip=true
```

`-Ddocker.skip=true` 不能省略：`docker-maven-plugin` 绑定在 package 阶段，默认会连上游作者的远程 Docker。构建完成后按以下顺序逐个启动（每个服务一个终端）：

```bash
java -jar mall-auth/target/mall-auth-1.0-SNAPSHOT.jar
java -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar
java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar
java -jar mall-search/target/mall-search-1.0-SNAPSHOT.jar
java -jar mall-gateway/target/mall-gateway-1.0-SNAPSHOT.jar
java -jar mall-monitor/target/mall-monitor-1.0-SNAPSHOT.jar
```

服务默认激活 `dev` profile，配置从 Nacos 拉取，**Nacos 必须先就绪**。后台启动方式与 IDEA 启动方式见 [STARTUP.md](STARTUP.md)。

### 3. 启动前端

要求 Node.js `^20.19.0 || >=22.12.0`：

```bash
cd mall-admin-web
npm ci
npm run dev           # http://localhost:5173

cd ../mall-app-web
npm ci
npm run dev:h5        # 默认也是 5173，与后台管理同时运行时加 -- --port 5174
npm run dev:mp-weixin # 微信小程序
```

前端通过 Vite 代理访问后端（`/api` 转发到 `localhost:8080` / `localhost:8085`，并去掉 `/api` 前缀），开发环境变量见各自的 `.env.development`。

### 4. 验证

```bash
# 经网关登录，返回 token 说明整条链路正常
curl -s -X POST "http://localhost:8201/mall-admin/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"macro123"}'
```

## 本地端口与账号

| 组件 | 宿主机端口 | 账号 |
| --- | --- | --- |
| MySQL | **3307**（映射容器 3306） | `root` / `root`，库 `mall` |
| Redis | **6380**（映射容器 6379） | 无密码 |
| MongoDB | 27017 | 库 `mall-port` |
| Elasticsearch | 9200 | 已关闭 security |
| RabbitMQ | 5672 / 15672 | `mall` / `mall`，vhost `/mall` |
| Nacos | 8848 / 9848 | 未开启鉴权，控制台 `/nacos` |
| MinIO | 9000 / 9001 | `minioadmin` / `minioadmin`，按需启动 |

后台管理默认账号：`admin` / `macro123`。

## 注意事项

- **端口不是默认值**：MySQL 用 **3307**、Redis 用 **6380**，`application.yml` 与 `mall-swarm/config/*/mall-*-dev.yaml` 两处需保持一致，Nacos 中的配置优先级更高。
- **MinIO 按需启动**：`docker compose --profile minio up -d`，不起不影响后端启动，只影响图片上传。
- **密钥为示例值**：数据库密码、RabbitMQ 账号、OSS/MinIO 密钥均为本地示例，部署到真实环境前务必替换；含敏感信息的本地配置请放在 `*.local.yaml`（已 gitignore）。
- **端口冲突**：mall-admin 用 8080、mall-search 用 8081，被占用时修改 `server.port` 并同步前端代理配置。

## 许可

代码版权归原作者所有，遵循 [Apache License 2.0](mall-swarm/LICENSE)。
