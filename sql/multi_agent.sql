-- ============================================================================
-- 多智能体创作流水线 DDL（novel_system 库）
-- 对应设计文档：docs/MULTI_AGENT_CREATION_DESIGN.md
-- 阶段1：creation_task / creation_stage_artifact（任务与阶段产物落库 + token 埋点）
-- 阶段2：World State 账本四张表 + book_info.world_setting（服务端世界观唯一事实源）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 阶段1：创作任务与阶段产物
-- ---------------------------------------------------------------------------

-- 创作任务（一次管道执行的记录，统计 token/耗时，为成本面板打底）
CREATE TABLE IF NOT EXISTS creation_task (
    id            BIGSERIAL PRIMARY KEY,
    book_id       BIGINT,
    chapter_id    BIGINT,
    mode          VARCHAR(20)  NOT NULL,                    -- FAST / STANDARD / REFINED / PRECISE
    status        VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',  -- PENDING / RUNNING / COMPLETED / FAILED
    current_stage VARCHAR(30),                             -- 当前推进到的阶段（断点续跑预留）
    degraded      BOOLEAN      NOT NULL DEFAULT FALSE,     -- 是否发生降级
    total_tokens  INTEGER,                                 -- 全程 LLM token 用量
    elapsed_ms    BIGINT,                                  -- 全程耗时（毫秒）
    error         TEXT,                                    -- 失败原因
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 异步任务入参（崩溃重启后重建管道入参；已建表的环境执行此句即可增量升级）
ALTER TABLE creation_task ADD COLUMN IF NOT EXISTS params JSONB;

CREATE INDEX IF NOT EXISTS idx_creation_task_book   ON creation_task(book_id);
CREATE INDEX IF NOT EXISTS idx_creation_task_status ON creation_task(status);

-- 阶段产物（每阶段结构化产物实时落库，异常中断后产物仍在，支持基于 last artifact 续跑）
CREATE TABLE IF NOT EXISTS creation_stage_artifact (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    stage      VARCHAR(30)  NOT NULL,               -- PLANNING / WRITING / CRITIC / POLISHING
    round      INTEGER      NOT NULL DEFAULT 1,     -- 重写轮次（首轮=1）
    artifact   JSONB        NOT NULL,               -- 结构化产物：outline / draft / review / polished
    tokens     INTEGER,                             -- 本阶段 token 用量
    elapsed_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_task ON creation_stage_artifact(task_id);

-- ---------------------------------------------------------------------------
-- 阶段2：World State 账本（共享上下文，多 Agent 一致性靠账本而非模型记性）
-- ---------------------------------------------------------------------------

-- 滚动前情摘要（book 级唯一，控制在 2000 字内）
CREATE TABLE IF NOT EXISTS novel_story_digest (
    book_id         BIGINT PRIMARY KEY,
    summary         TEXT NOT NULL,
    last_chapter_id BIGINT,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 章节大纲（Planner 产物，含修订版本）
CREATE TABLE IF NOT EXISTS novel_chapter_outline (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    chapter_id BIGINT,
    outline    JSONB  NOT NULL,                     -- ChapterOutline 序列化
    version    INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outline_book ON novel_chapter_outline(book_id);

-- 伏笔账本（核心资产：OPEN / RESOLVED / ABANDONED）
CREATE TABLE IF NOT EXISTS novel_plot_threads (
    id                  BIGSERIAL PRIMARY KEY,
    book_id             BIGINT NOT NULL,
    description         TEXT NOT NULL,
    planted_chapter_id  BIGINT,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_chapter_id BIGINT,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_plot_threads_book_status ON novel_plot_threads(book_id, status);

-- 人物动态状态（角色卡是静态设定，这张是随剧情推进的当前态；book+character 唯一）
CREATE TABLE IF NOT EXISTS novel_character_state (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    chapter_id   BIGINT,                             -- 状态快照至哪章
    state        JSONB  NOT NULL,                    -- {位置, 心态, 新获知信息, 持有物品, 受伤/能力变化}
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_char_state UNIQUE (book_id, character_id)
);

-- 世界观唯一事实源：book_info 增加 world_setting 列（前端仍可传，仅作覆盖提示）
ALTER TABLE book_info ADD COLUMN IF NOT EXISTS world_setting TEXT;

-- ---------------------------------------------------------------------------
-- 阶段4：Kafka 异步任务依赖（幂等栅栏由 creation_task.status 承担，无需额外表）
-- topic 由应用启动时自动创建（KafkaConfig）：
--   novel.creation.task      整章/批量生成异步任务
--   novel.chapter.finalized  章节内容定稿（摘要滚动 + 人物状态抽取）
-- ---------------------------------------------------------------------------
