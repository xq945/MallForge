# mall-swarm-suite

mall 电商系统全家桶，把后端微服务与两套前端整合在同一个仓库里，便于统一阅读、构建和联调。

## 仓库结构

| 目录 | 说明 | 技术栈 |
| --- | --- | --- |
| [`mall-swarm/`](mall-swarm/) | 后端微服务 | Java 17 · Spring Boot 3.5.14 · Spring Cloud 2025.0.2 · Spring Cloud Alibaba 2025.0.0.0 |
| [`mall-admin-web/`](mall-admin-web/) | 后台管理前端 | Vue 3 · Vite · Element Plus · TypeScript |
| [`mall-app-web/`](mall-app-web/) | 移动端商城前端 | Vue 3 · uni-app（H5 / 小程序 / App） |

后端模块划分：

- `mall-gateway` — 网关
- `mall-auth` — 认证中心
- `mall-admin` — 后台管理服务
- `mall-portal` — 前台商城服务
- `mall-search` — 搜索服务（Elasticsearch）
- `mall-monitor` — 监控中心（Spring Boot Admin）
- `mall-common` / `mall-mbg` / `mall-demo` — 公共模块、代码生成、示例

## 版本基线

本仓库为 [macrozheng](https://github.com/macrozheng) 上游代码的整合快照，对应以下 commit：

| 子项目 | 上游仓库 | commit |
| --- | --- | --- |
| mall-swarm | [macrozheng/mall-swarm](https://github.com/macrozheng/mall-swarm) | `04c442f` |
| mall-admin-web | [macrozheng/mall-admin-web](https://github.com/macrozheng/mall-admin-web) | `81fc17e` |
| mall-app-web | [macrozheng/mall-app-web](https://github.com/macrozheng/mall-app-web) | `b1eb3cd` |

如需获取上游更新，请直接对照上表对应的仓库。

## 快速开始

### 后端

```bash
cd mall-swarm
mvn clean package
# 运行各服务前，请先准备好 MySQL / Redis / MongoDB / Elasticsearch / RabbitMQ
# 配置位于 config/ 与各模块的 src/main/resources/application.yml
```

### 后台管理前端

要求 Node.js `^20.19.0 || >=22.12.0`：

```bash
cd mall-admin-web
npm install
npm run dev
```

### 移动端前端

```bash
cd mall-app-web
npm install
npm run dev:h5        # H5
npm run dev:mp-weixin # 微信小程序
```

> 仓库内的数据库密码、OSS/MinIO 密钥均为上游默认示例值，部署到真实环境前务必替换。

## 许可

代码版权归原作者所有，遵循 [Apache License 2.0](mall-swarm/LICENSE)。
