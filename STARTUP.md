# 启动指南

本文描述日常开发时如何启动 mall-swarm 全套服务（中间件 + 6 个后端 + 2 个前端）。
首次搭建环境（拉镜像、建库、导入 Nacos 配置、安装依赖）请先看 [LOCAL-DEV.md](LOCAL-DEV.md)。

## 0. 前置检查

```bash
# 中间件容器是否在运行
docker compose ps

# 后端是否已构建（六个模块都应有 jar）
ls mall-swarm/*/target/*.jar
```

若容器未运行，先执行 `docker compose up -d`；若 jar 不存在，执行：

```bash
cd mall-swarm
mvn clean package -DskipTests -Ddocker.skip=true
```

> 必须加 `-Ddocker.skip=true`，否则 package 阶段会尝试连接上游作者的远程 Docker，构建失败。

## 1. 启动中间件

```bash
cd mall-swarm-suite
docker compose up -d
```

等待 MySQL、ES 变为 healthy（`docker compose ps` 查看）。服务启动时从 Nacos 拉配置，**Nacos 必须先就绪**。

| 组件 | 地址 |
| --- | --- |
| Nacos 控制台 | http://localhost:8848/nacos |
| MySQL | localhost:3307（root/root，库 mall） |
| Redis | localhost:6380 |
| MongoDB | localhost:27017 |
| Elasticsearch | http://localhost:9200 |
| RabbitMQ 控制台 | http://localhost:15672（mall/mall） |

## 2. 启动后端

在 `mall-swarm` 目录下，按下面顺序**各开一个终端**启动（顺序不严格，但 auth 与基础服务先起、gateway 最后起更稳妥）：

| 顺序 | 服务 | 端口 | 命令 |
| --- | --- | --- | --- |
| 1 | mall-auth | 8401 | `java -jar mall-auth/target/mall-auth-1.0-SNAPSHOT.jar` |
| 2 | mall-admin | 8080 | `java -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar` |
| 3 | mall-portal | 8085 | `java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar` |
| 4 | mall-search | 8081 | `java -jar mall-search/target/mall-search-1.0-SNAPSHOT.jar` |
| 5 | mall-gateway | 8201 | `java -jar mall-gateway/target/mall-gateway-1.0-SNAPSHOT.jar` |
| 6 | mall-monitor | 8101 | `java -jar mall-monitor/target/mall-monitor-1.0-SNAPSHOT.jar` |

看到 `Started XxxApplication in ... seconds` 即启动成功。

### 后台启动（可选）

不想开六个终端时，可在 `mall-swarm` 目录执行：

```bash
mkdir -p ../logs

nohup java -jar mall-auth/target/mall-auth-1.0-SNAPSHOT.jar    > ../logs/mall-auth.log    2>&1 &
nohup java -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar   > ../logs/mall-admin.log   2>&1 &
nohup java -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar > ../logs/mall-portal.log  2>&1 &
nohup java -jar mall-search/target/mall-search-1.0-SNAPSHOT.jar > ../logs/mall-search.log  2>&1 &
nohup java -jar mall-gateway/target/mall-gateway-1.0-SNAPSHOT.jar > ../logs/mall-gateway.log 2>&1 &
nohup java -jar mall-monitor/target/mall-monitor-1.0-SNAPSHOT.jar > ../logs/mall-monitor.log 2>&1 &
```

启动后可用 `jps -l` 查看 Java 进程；停止时按 PID 执行 `taskkill //PID <pid> //F`。

日志位于仓库根目录的 `logs/`，排查时直接看对应文件。

### 用 IDEA 启动

直接运行各模块的 `MallXxxApplication` 主类即可，默认已激活 `dev` profile，无需额外参数。注意同一端口只能有一个实例。

## 3. 启动前端

```bash
# 后台管理（默认 5173）
cd mall-admin-web
npm run dev

# 移动端 H5（默认也是 5173，与后台管理同时跑时需换端口）
cd mall-app-web
npm run dev:h5 -- --port 5174
```

前端通过各自的 `vite.config.ts` 把 `/api` 代理到后端（`mall-admin-web` → `localhost:8080`，`mall-app-web` → `localhost:8085`，并去掉 `/api` 前缀），因此无需处理跨域。

## 4. 启动后验证

```bash
# 1) Nacos 中应看到 6 个服务
curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20"

# 2) 经网关登录，返回 token 说明整条链路正常
curl -s -X POST "http://localhost:8201/mall-admin/admin/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"macro123"}'

# 3) 各服务健康端点（返回 200）
curl -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/health
```

默认后台账号：`admin` / `macro123`。

## 5. 停止服务

- **后端**：在各自终端按 `Ctrl+C`；后台启动的可用
  `taskkill //F //IM java.exe`（会结束本机**所有** Java 进程，谨慎）或按 PID 结束。
- **中间件**：`docker compose stop` 停止但保留容器；`docker compose down` 删除容器，数据卷保留；加 `-v` 才会删除数据。

## 6. 常见启动失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Port 8080 was already in use` | 端口被其他程序占用 | 结束占用进程，或修改 `application.yml` 的 `server.port`（并同步前端 `.env`） |
| 启动时报 Nacos 连接失败 / 配置为空 | Nacos 未启动或配置未导入 | 确认容器运行，执行 `python scripts/import-nacos-config.py` |
| mall-search 报 `analyzer [ik_max_word] has not been configured` | ES 缺少 IK 插件 | 执行 `bash scripts/setup-es-ik.sh` 后 `docker compose up -d elasticsearch` |
| MySQL 连接失败 | 端口用错 | 容器映射是 **3307**，不是 3306 |
| Redis 连接失败 | 端口用错 | 容器映射是 **6380**，不是 6379 |
| 前端依赖报错 / 找不到 vite | 未安装依赖或 Node 版本不符 | `npm ci`，Node 需 `^20.19` 或 `>=22.12` |
