# 🖋️ 灵墨小说创作平台（LingMo Novel Creation Platform）

> **灵墨** · 多智能体长篇小说创作平台
> 「灵」取灵感、灵性，「墨」取笔墨、墨迹 —— 灵感落墨，执笔成书。
>
> 中文名：灵墨小说创作平台 ｜ 英文名：LingMo Novel Creation Platform ｜ 简称：灵墨

灵墨是一个基于 **Spring Boot 3.4 + Spring AI** 的多智能体小说创作平台。它把「一次提示词续写」升级为可审计的编辑部流水线：**Planner 策划 → Writer 主笔 → 确定性规则 + Critic 审查 → 定向重写 → Polisher 润色**，并用 World State 正典账本保证跨章的人物、时间线、伏笔与设定一致性。

## ✨ 核心特性

### 🎬 多智能体创作流水线
- **四档创作模式**：FAST（Writer 单链路）/ STANDARD（Writer + Critic 闭环）/ REFINED（+ Planner + Polisher）/ PRECISE（+ 并行双稿择优），按「质量—成本」自由选择。
- **同步 SSE + 异步任务**：`/pipeline` 实时推送 `stage` / `content` 事件；`/pipeline/async` 经 Kafka 异步执行，可轮询任务进度与 run 汇总。
- **有限重写回路**：Critic 不通过时带问题清单定向重写，默认 2 轮、硬上限 3 轮；预算耗尽按 L1–L4 阶梯降级。
- **单章断点续跑**：节点级 Checkpoint（幂等键 + 输入指纹），中断后从首个未完成节点继续，已完成节点不重复计费。

### 🧠 正典世界状态（World State）

> 摘要树、三时间轴与可信度分级由 `novel.pipeline.features.*` 消融开关控制，默认关闭；规则类判定另受 `novel.pipeline.rules.*` 状态控制。

- **唯一事实源**：`book_info.world_setting` 世界设定、滚动前情摘要与定长摘要树（章 / 段 / 书三级）。
- **人物状态三时间轴**：事实账本软失效、按叙事时刻求解，不物理删除历史事实。
- **关系与事件**：角色关系生效区间、事件因果链、伏笔排期与 `knows` 闭世界知情集合。
- **RAG 检索**：PGVector 按 `bookId` 过滤，召回历史章节片段作为写作与审查证据。
- **抽取与断言**：统一结构化抽取契约，断言可信度分级（FACT / LIE / HEARSAY / HYPOTHETICAL / DREAM）与冲突入审。

### 🤖 有界自主 Agent（L2）
- **自研 AgentRuntime**：每步由模型输出决策（`tool_call` / `ask_human` / `finish`），代码执行工具、持久化回执、再喂回下一步——循环归代码，步骤决策归模型。
- **工具白名单**：`canon.query`、`canon.timeline`、`evidence.search`、`rulecheck.run`、`proposal.emit` 等，按节点开放，读范围不等于写授权。
- **围栏与降级**：步数 / token / 墙钟 / 防复读熔断；失败或超限自动回退 L1 单次调用，L1 仍败则产出 DEGRADED 草稿转人审。
- **提案机制**：Writer 只能 `proposal.emit` 暂存正典提案，提交门在节点成功后统一裁定 ADOPTED / REJECTED。
- **自主性矩阵**：`mode × node → level` 配置；FAST / STANDARD 默认 L1 控成本，REFINED / PRECISE 三节点开 L2，支持请求级覆盖。

### 🛡️ 质量与安全
- **确定性规则引擎**：RC 系列规则（人物生死、位置跳变、因果时序、称谓关系、伏笔逾期、知识边界、POV、字数等），支持 `OFF / SHADOW / GRADUAL / ACTIVE` 生命周期（默认 OFF，按影子 → 灰度 → 全量逐步上线）、作者豁免与叙诡保护区。
- **Critic 异源模型**：可配置与 Writer 不同系的评审模型，抑制同源自我偏袒；未配置时回退主模型。
- **三层安全防线**：`InputGuard` 输入前置校验 → `StreamGuard` 流式中置拦截 → `FinalDraftGate` 定稿词表 gate，全过程写入安全审计账本。
- **收敛控制器**：成对比较回滚、best-of 留档与循环预算，避免越改越差。
- **人审队列**：冲突 / 断言 / 安全 / 未完成四类卡点统一 REST，支持人工裁定后回流。

### 💰 可靠性与成本
- **预算三级链**：书级硬上限 → 章级 token / 时延 / 成本 → 轮级 40% 配额。
- **L1–L4 降级阶梯**：关 best-of → 收紧重写 → 跳过 Critic/Polisher → 停笔转人工；安全与硬校验不进入降级域。
- **成本账本**：按 stage / Agent / model 计价，支持汇总查询与 CSV 导出。
- **审计账本**：Agent 步骤、工具回执、引文链接、提案与安全日志均可追溯。

### 🧪 实验与可复现
- **模型调用录制 / 回放**：JSONL 落盘并可归档 MinIO，实验失败样本可原样重放。
- **5 个消融开关**：`summaryTree` / `triTimeline` / `credibility` / `ruleRouting` / `convergenceGuard`，默认全关 = 现状行为，支持请求级覆盖做同部署双跑。
- **测试**：55 个测试类、460+ 用例；真实模型冒烟测试默认跳过，需显式开启。

### 📚 基础能力
- JWT 认证与用户体系、作者主页、角色卡片与关系网络
- 书架、章节阅读、分类与榜单
- 知识库 / 自定义知识库（PDF 导入、切分、向量化）
- Kafka 事件驱动、MinIO 对象存储、Knife4j + OpenAPI 3 接口文档

## 🧭 创作流水线

```
POST /api/book/create/ai/pipeline            （SSE：stage + content）
POST /api/book/create/ai/pipeline/async      （Kafka 异步，返回 taskId）
                │
                ▼
      CreationPipelineService
   （状态机 / 预算 / 降级 / 断点 / 进度推送）
                │
   ┌────────────┴─────────────────────────────────────────────┐
   │  Planner ──→ Writer ──→ RuleChecker + Critic ──→ 通过？   │
   │  大纲        正文        规则/审查/引文          │否       │
   │                                                  └─ 定向重写（K≤2）
   │                              └──→ Polisher ──→ 提交门 ──→ 定稿
   └────────────┬─────────────────────────────────────────────┘
                ▼
   World State 账本（PostgreSQL）
   + RAG（PGVector） + 记忆（Redis） + 录制归档（MinIO）
```

| 模式 | 链路 | 相对成本 | 适用场景 |
|------|------|----------|----------|
| FAST | Writer 单链路 | 1x | 灵感速记、快速试写 |
| STANDARD | Writer + Critic（含重写回路） | 2–4x | 日常创作默认档 |
| REFINED | Planner + Writer + Critic + Polisher | 5–8x | 精修、定稿 |
| PRECISE | REFINED + 并行双稿择优 | 8–12x | 实验档、重要章节 |

## 🛠️ 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4.5 |
| JDK | Java 17 |
| AI 框架 | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0 |
| 大模型 | OpenAI 兼容协议接入（默认 DeepSeek 系对话 + 通义 `text-embedding-v4` 向量；Critic 可选异源模型） |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | PostgreSQL（业务库 + 向量库双数据源） |
| 向量库 | PGVector |
| 缓存 / 记忆 | Redis + Redisson |
| 消息队列 | Apache Kafka |
| 对象存储 | MinIO |
| API 文档 | Knife4j（OpenAPI 3） |
| 认证 | JWT + BCrypt |
| 工具库 | Hutool、Lombok、Gson、TransmittableThreadLocal |

## 📁 项目结构

```
novel-creation-platform-v2/
├── src/main/java/com/linpj/novel/create/
│   ├── agent/          # 多智能体：Planner/Writer/Critic/Polisher + AgentRuntime + 工具
│   ├── pipeline/       # 创作流水线编排、预算守卫、进度推送
│   ├── worldstate/     # World State 账本：状态/事件/伏笔/提案/成本/断点/摘要树
│   ├── rule/           # 确定性一致性规则引擎（RC 系列）
│   ├── security/       # InputGuard / StreamGuard / FinalDraftGate / 安全审计
│   ├── config/         # AI、双数据源、Redis、Kafka、MinIO、流水线与开关配置
│   ├── controller/     # REST 控制器（含 SSE 流式创作入口）
│   ├── service/        # 业务逻辑层（impl 为实现）
│   ├── mapper/         # MyBatis Mapper 接口
│   ├── pojo/           # entity / dto / vo / pipeline / common
│   ├── consumer/       # Kafka 消费者（创作任务、章节定稿）
│   ├── producer/       # Kafka 生产者
│   ├── memory/         # 会话记忆装配
│   ├── context/        # 用户上下文（ThreadLocal）
│   ├── interceptor/    # 认证鉴权拦截器
│   ├── exception/      # 全局异常与错误码
│   ├── constant/       # 常量与统一响应封装
│   └── utils/          # JWT、AES、文件处理等
├── src/main/resources/
│   ├── application.yml     # 默认配置（dev 配置不入库，本地覆盖）
│   ├── mapper/             # MyBatis XML
│   └── statis/             # logback 日志配置
├── src/test/               # 单元测试与冒烟测试
├── sql/
│   ├── multi_agent.sql     # 多智能体累积 DDL（可重复执行）
│   ├── knowledge_base.psql # 知识库 / 向量表
│   └── character_cards.sql # 角色卡基础表
└── docs/                   # 设计文档（本地维护，按约定不入库）
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 15+（业务库需启用 `pgvector` 扩展）
- Redis 6+
- Apache Kafka
- MinIO（知识库文件存储与模型调用录制归档）

### 1. 克隆项目

```bash
git clone https://github.com/PeijiLin/lingma-AI-novel-created.git
cd lingma-AI-novel-created
```

### 2. 初始化数据库

```bash
# 创建业务库与向量库
psql -U postgres -c "CREATE DATABASE novel_system;"
psql -U postgres -c "CREATE DATABASE novel_knowledge_base;"

# 知识库表 + pgvector 扩展（向量维度需与 embedding 配置一致）
psql -U postgres -d novel_knowledge_base -f sql/knowledge_base.psql

# 业务表：角色卡 + 多智能体累积 DDL（脚本幂等，可重复执行）
psql -U postgres -d novel_system -f sql/character_cards.sql
psql -U postgres -d novel_system -f sql/multi_agent.sql
```

> `knowledge_base.psql` 中 `vector(1536)` 为示例维度。若使用 `text-embedding-v4`（默认 1024 维），请同步修改建表维度与 `spring.ai.openai.embedding.options.dimensions`。

### 3. 本地配置

`src/main/resources/application-dev.yml` 已被 `.gitignore` 忽略，请在本地创建并覆盖默认值（仓库内 `application.yml` 只保留占位配置，请勿提交真实密钥）：

```yaml
spring:
  datasource:
    novel-system:
      jdbc-url: jdbc:postgresql://localhost:5432/novel_system
      username: postgres
      password: your-password
    novel-knowledge:
      jdbc-url: jdbc:postgresql://localhost:5432/novel_knowledge_base
      username: postgres
      password: your-password
  data:
    redis:
      host: localhost
      port: 6379
      password: your-password
      database: 15
  kafka:
    bootstrap-servers: localhost:9092
  ai:
    openai:
      api-key: sk-xxxx
      chat:
        base-url: https://your-openai-compatible-endpoint
        options:
          model: deepseek-v3.2
      embedding:
        api-key: sk-xxxx
        base-url: https://dashscope.aliyuncs.com/compatible-mode
        options:
          model: text-embedding-v4
          dimensions: 1024
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucketFileName: novel-files
```

> 对话模型走 OpenAI 兼容协议，`base-url` 不要带末尾 `/v1`。Critic 异源模型可选配置 `novel.pipeline.models.critic.*`，未配置时回退主模型。

### 4. 启动项目

```bash
mvn spring-boot:run
```

服务启动后访问：

- 服务根路径：`http://localhost:8088/api`
- Knife4j 文档：`http://localhost:8088/api/doc.html`
- OpenAPI JSON：`http://localhost:8088/api/v3/api-docs`

### 5. 发起一次创作

```bash
curl -N -X POST http://localhost:8088/api/api/book/create/ai/pipeline \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "bookId": 1,
    "chapterId": 1,
    "userInput": "主角在雨夜重返旧宅，发现密室被人提前打开。",
    "mode": "STANDARD",
    "maxRewriteRounds": 2,
    "targetWordCount": 3000
  }'
```

响应为 SSE 事件流，包含阶段进度（`PLANNING / WRITING / CRITIC / POLISHING / COMPLETED`）与正文增量。

## 📡 主要 API

| 模块 | 接口前缀 | 说明 |
|------|----------|------|
| 认证与用户 | `/api/user` | 注册、登录、刷新、登出、个人资料 |
| 作者主页 | `/api/author` | 当前作者信息与更新 |
| 创作流水线 | `/api/api/book/create/ai` | `pipeline` / `async` / `task/{id}` / `run/{runId}`，以及 `polish` / `create` / `continue` / `dialogue` / `activate` 五个兼容端点 |
| 小说管理 | `/api/creation` | 创建、更新、分页、详情 |
| 阅读与章节 | `/api/book` | 分类、分页、正文、章节、发布、榜单 |
| 角色卡 | `/api/api/character-card` | 增删改查与按书搜索 |
| 书架 | `/api/bookshelf` | 加入书架 |
| 知识库 | `/api/knowledgeBase` | 创建、分页、删除 |
| 自定义知识库 | `/api/customizeKnowledgeBase` | 文件 / 内容上传并向量化 |
| 人审队列 | `/api/api/book/review-queue` | 冲突、断言、安全、未完成四类卡点 |
| 规则豁免 | `/api/api/book/rule-exemption` | 豁免登记、到期复核 |
| 成本账本 | `/api/api/book/cost` | 汇总查询、CSV 导出 |

> 服务 `context-path` 为 `/api`；部分较早的控制器路径自身带 `/api` 前缀，因此形成 `/api/api/...` 的兼容路径，前端已按真实路径适配。

## ⚙️ 关键配置

| 配置前缀 | 用途 |
|----------|------|
| `novel.pipeline.features.*` | 5 个消融开关（默认全关 = 现状行为） |
| `novel.pipeline.autonomy.*` | 自主性矩阵：模式 × 节点 → level / maxSteps / tokenBudget |
| `novel.pipeline.models.critic.*` | Critic 异源模型；未配置回退主模型 |
| `novel.pipeline.budget.*` | 预算三级链（书 / 章 / 轮） |
| `novel.pipeline.pricing.*` | 成本价目表（每千 token 单价） |
| `novel.pipeline.recording.*` | 模型调用录制目录与 MinIO 桶 |
| `novel.pipeline.rules.*` | 规则状态：`OFF / SHADOW / GRADUAL / ACTIVE` |

## 🧪 测试

```bash
# 单元测试
mvn test

# 真实模型冒烟测试（会消耗 token，默认跳过）
mvn -Dtest=CriticL2SmokeTest -Dsmoke=true test
```

> 真实模型冒烟测试需要可用的模型 API 与完整中间件环境，不建议在 CI 中默认执行。

## 🖥️ 灵墨前端

灵墨 Web 前端位于同级仓库 `novel-creation-front`（Vue 3 + Vite + Tailwind CSS 4 + shadcn-vue），开发服务器默认代理到 `http://localhost:8088`：

```bash
npm install
npm run dev
```

## 📄 许可证

仅供学习参考使用。
