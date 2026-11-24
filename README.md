# lingmo-AI-novel-created

AI 小说创作平台，采用前后端单仓（monorepo）布局。

## 目录结构

```
lingmo-AI-novel-created/
├── lingmo-server/      # Spring Boot 3.4.5 / Java 17 后端
│   ├── src/            # 源码与测试
│   ├── sql/            # 数据库增量脚本
│   ├── pom.xml
│   └── README.md       # 后端详细说明
├── lingmo-web/         # 前端（规划中，暂未创建）
├── AGENTS.md           # 仓库级开发约定（含后端命令与陷阱）
└── .gitignore
```

> `lingmo-server/` 由 `feature/trusted-pipeline-v2` 迁移而来，原后端目录已完整移入，代码与 Git 历史保持不变。

## 后端快速开始

所有 Maven 命令都在 `lingmo-server/` 下执行（仓库根目录没有 `pom.xml`）：

```bash
cd lingmo-server
mvn test            # 单元测试（约 30 秒，不依赖 Postgres / Redis / Kafka）
mvn spring-boot:run # 启动服务
```

- 默认端口：`8088`
- context path：`/api`
- 接口地址示例：`http://localhost:8088/api/...`
- 数据库脚本：`lingmo-server/sql/`（需现有 `novel_system` 基础库）

数据库初始化顺序与配置项说明见 [lingmo-server/README.md](lingmo-server/README.md)，开发约定与已知陷阱见 [AGENTS.md](AGENTS.md)。

## 前端

`lingmo-web/` 规划中。前端工程将独立放在该目录，不要在仓库根目录放 `package.json`。

## 许可证

本项目仅供学习参考使用。
