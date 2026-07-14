# 📚 小说创作平台 (Novel Creation Platform)

一个基于 **Spring Boot 3 + AI** 的智能小说创作平台，集成大语言模型（LLM）辅助创作、RAG 知识库、角色卡片管理、书架系统等功能，为作者提供全方位的创作支持。

## ✨ 功能特性

- 🤖 **AI 智能创作** — 接入 DeepSeek / 通义千问等大模型，支持 AI 辅助小说内容生成
- 🧠 **RAG 知识库** — 基于向量数据库（PGVector）构建小说创作知识库，支持 PDF 文档导入
- 🎭 **角色卡片系统** — 管理小说中的角色信息及其关系网络
- 📖 **书架系统** — 用户个人书架，支持阅读管理
- 💬 **AI 对话** — 基于 Redis 的记忆对话系统，支持多轮上下文交互
- 📂 **知识库管理** — 支持自定义知识库的创建与管理
- 🔐 **用户认证** — JWT Token 鉴权，BCrypt 密码加密
- 📡 **异步消息** — 基于 Kafka 的事件驱动架构
- 🗄️ **对象存储** — MinIO 文件存储支持
- 📋 **API 文档** — Knife4j + OpenAPI 3 自动生成接口文档

## 🛠️ 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.0 |
| JDK | Java 17 |
| ORM | MyBatis-Plus 3.5.14 |
| 数据库 | PostgreSQL |
| 缓存 | Redis + Redisson |
| AI 框架 | Spring AI 1.0.0 + Spring AI Alibaba |
| 向量数据库 | PGVector |
| 大模型 | DeepSeek-V3.2 / 通义千问 (text-embedding-v4) |
| 消息队列 | Apache Kafka |
| 对象存储 | MinIO |
| API 文档 | Knife4j (OpenAPI 3) |
| 认证 | JWT + BCrypt |
| 工具库 | Hutool、Lombok、Gson |

## 📁 项目结构

```
src/main/java/com/linpj/novel/create/
├── config/          # 配置类（AI、Redis、Kafka、MinIO、数据源等）
├── constant/        # 常量定义与统一响应封装
├── consumer/        # Kafka 消息消费者
├── producer/        # Kafka 消息生产者
├── controller/      # REST API 控制器
├── service/         # 业务逻辑层
│   └── impl/        # 服务实现类
├── mapper/          # MyBatis Mapper 接口
├── pojo/            # 数据模型（Entity / DTO / VO）
├── interceptor/     # 拦截器（认证鉴权）
├── exception/       # 全局异常处理
├── utils/           # 工具类（JWT、AES、文件处理等）
└── context/         # 用户上下文（ThreadLocal）
```

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 15+（需启用 pgvector 扩展）
- Redis 6+
- Apache Kafka
- MinIO

### 1. 克隆项目

```bash
git clone https://github.com/your-username/novel-creation-platform-v2.git
cd novel-creation-platform-v2
```

### 2. 初始化数据库

```bash
# 创建主数据库
psql -U postgres -c "CREATE DATABASE novel_system;"

# 导入知识库向量扩展
psql -U postgres -d novel_knowledge_base -f sql/knowledge_base.psql

# 导入角色卡片表
psql -U postgres -d novel_system -f sql/character_cards.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`，修改数据库、Redis、Kafka、MinIO 及 AI API Key 等配置项。

### 4. 启动项目

```bash
mvn spring-boot:run
```

服务启动后访问：
- API 地址：`http://localhost:8080/api`
- Swagger 文档：`http://localhost:8080/api/doc.html`

## 📡 主要 API 接口

| 模块 | 路径 | 说明 |
|------|------|------|
| 用户 | `/user` | 注册、登录、用户信息 |
| AI 创作 | `/ai-chat` | AI 对话、智能创作 |
| 小说创作 | `/creation-book` | 小说创建与管理 |
| 角色卡片 | `/character-card` | 角色卡片 CRUD |
| 书架 | `/bookshelf` | 书架管理 |
| 阅读 | `/reading` | 章节阅读 |
| 知识库 | `/knowledge-base` | 知识库管理 |
| 自定义知识库 | `/customize-knowledge` | 自定义知识库 |

## 📄 许可证

本项目仅供学习参考使用。
