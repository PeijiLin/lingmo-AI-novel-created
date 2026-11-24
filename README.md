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
├── lingmo-web/         # 前端（Vue 3 + Vite + TypeScript）
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

## 前端快速开始

前端工程位于 `lingmo-web/`（Vue 3 + Vite + TypeScript，npm）。所有前端命令都在该目录下执行，仓库根目录不放 `package.json`：

```bash
cd lingmo-web
npm ci                # 安装依赖（需 Node ^20.19 或 >=22.12）
npm run dev           # 开发服务器 5173，/api 代理到 localhost:8088
npm run build         # 类型检查 + 生产构建
npm run test:unit -- --run  # Vitest 单测
npx eslint .          # 代码检查（npm run lint 会自动修复）
```

> Windows PowerShell 下若 `npm` 被执行策略拦截，改用 `npm.cmd`。
> 前端由原 `novel-creation-front` 仓库以 `git subtree` 导入，历史保留；后续同步：`git subtree pull --prefix=lingmo-web front master`。

## 许可证

本项目仅供学习参考使用。
