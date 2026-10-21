# 本地开发启动说明

本文件记录在 Windows + Docker Desktop 下**首次**搭建 mall-swarm 环境的步骤。
所有中间件由根目录的 [`docker-compose.yml`](docker-compose.yml) 管理。

> 环境已就绪后，日常的启动顺序、命令和验证方式见 [STARTUP.md](STARTUP.md)。

## 端口一览

| 组件 | 宿主机端口 | 说明 |
| --- | --- | --- |
| MySQL | **3307** | 映射容器 3306，避开本机已有 MySQL；库 `mall` / `root` / `root` |
| Redis | **6380** | 映射容器 6379，无密码 |
| MongoDB | 27017 | 库 `mall-port` |
| Elasticsearch | 9200 | 8.18.8，已关闭 security |
| RabbitMQ | 5672 / 15672 | 用户 `mall` / `mall`，vhost `/mall` |
| Nacos | 8848 / 9848 | 2.5.1 单机模式，未开启鉴权，控制台 `/nacos` |
| MinIO | 9000 / 9001 | 默认不启动，见下文 |

后端服务端口：gateway `8201`、admin `8080`、auth `8401`、portal `8085`、search `8081`、monitor `8101`。

## 首次准备

```bash
# 1. 启动中间件（MySQL 首次启动会自动建库并导入 mall.sql）
docker compose up -d

# 2. 下载 ES 的 IK 分词插件（mall-search 的索引映射需要 ik_max_word）
bash scripts/setup-es-ik.sh
docker compose up -d elasticsearch   # 让 ES 加载插件

# 3. 导入 Nacos 配置（admin / portal / search / gateway 各一份 dev 配置）
python scripts/import-nacos-config.py

# 4. 构建后端
#    注意：pom 里的 docker-maven-plugin 绑定在 package 阶段，
#    默认会连上游作者的 192.168.3.101:2375 打镜像，本地构建必须跳过
cd mall-swarm && mvn clean package -DskipTests -Ddocker.skip=true

# 5. 前端依赖
cd ../mall-admin-web && npm ci
cd ../mall-app-web && npm ci
```

## 启动

后端在 `mall-swarm` 目录下逐个启动（默认 `dev` profile，配置从 Nacos 拉取）：

```bash
java -jar mall-auth/target/mall-auth-1.0-SNAPSHOT.jar
java -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar
java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar
java -jar mall-search/target/mall-search-1.0-SNAPSHOT.jar
java -jar mall-gateway/target/mall-gateway-1.0-SNAPSHOT.jar
java -jar mall-monitor/target/mall-monitor-1.0-SNAPSHOT.jar
```

前端：

```bash
cd mall-admin-web && npm run dev          # 默认 5173
cd mall-app-web   && npm run dev:h5       # 默认也是 5173
# 两个同时运行时给其中一个指定端口：
npm run dev:h5 -- --port 5174
```

验证：`POST http://localhost:8201/mall-admin/admin/login`，账号 `admin` / `macro123`，返回 token 即链路打通。

## 说明与排错

- **Nacos 配置**：源文件在 [`mall-swarm/config/`](mall-swarm/config/)，导入脚本只导入 dev 配置。改了配置后重新执行脚本即可覆盖。
- **MinIO**：镜像已迁移到 `quay.io/minio/minio`，compose 中用 `--profile minio` 按需启动；不起也不影响后端启动，只影响图片上传。
- **端口冲突**：`mall-admin` 默认 8080、`mall-search` 默认 8081，若被其它程序占用会启动失败，按需在 `application.yml` 改 `server.port`（并同步前端 `VITE_BASE_SERVER_URL`）。
- **Docker 拉镜像超时**：Docker Desktop 需配置 HTTP/HTTPS 代理（Settings → Resources → Proxies），否则直连 Docker Hub 会超时。
- **改数据库/Redis 端口**：本地 `application.yml` 与 `mall-swarm/config/*/mall-*-dev.yaml` 两处都要改，Nacos 中的配置优先级更高。
