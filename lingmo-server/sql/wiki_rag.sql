-- ============================================================================
-- 灵墨 · Wiki 正典层 + 知识库/向量统一模型（单库 lingmo）
-- 依据：docs/WIKI_RAG_UNIFIED_PLAN.md 第三节
-- 执行库：192.168.220.138:5432/lingmo（需 CREATE EXTENSION vector）
-- 编码：UTF-8（无 BOM）
-- 说明：
--   1) vector_store 由 Spring AI PgVectorStore 托管，不在本文件建表；
--      扩展字段一律写 metadata JSONB（bookId/chunkId/pageId/anchorId/kind）。
--   2) 旧 knowledge_base 表仅留过渡，M2 完成后由 novel_kb_source 取代。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Wiki 正典层
-- ---------------------------------------------------------------------------

-- 正典页：一类实体一页；entity_key 为库内唯一归一化键
CREATE TABLE IF NOT EXISTS novel_wiki_page (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT NOT NULL,
    entity_type VARCHAR(20) NOT NULL,          -- CHARACTER / LOCATION / FACTION / ITEM / WORLD_RULE / STYLE(预留)
    entity_id   BIGINT,                        -- 对应 character_cards.id 等；纯设定页可空
    entity_key  VARCHAR(160) NOT NULL,         -- char:12 / loc:青云山 / rule:灵力体系
    title       VARCHAR(200) NOT NULL,
    canonical   TEXT,                          -- 页卡正文（注入时可截断）
    attrs       JSONB NOT NULL DEFAULT '{}'::jsonb,  -- 硬事实白名单
    status      VARCHAR(20) NOT NULL DEFAULT 'CANON', -- DRAFT / CANON / DEPRECATED
    version     INTEGER NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_wiki_page UNIQUE (book_id, entity_type, entity_key)
);
CREATE INDEX IF NOT EXISTS idx_wiki_page_book ON novel_wiki_page(book_id, entity_type);

-- 别名：实体定位（不走相似度）
CREATE TABLE IF NOT EXISTS novel_wiki_alias (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    page_id    BIGINT NOT NULL REFERENCES novel_wiki_page(id) ON DELETE CASCADE,
    alias      VARCHAR(160) NOT NULL,
    normalized VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_wiki_alias UNIQUE (book_id, normalized)
);
CREATE INDEX IF NOT EXISTS idx_wiki_alias_page ON novel_wiki_alias(page_id);

-- 锚点：每条硬事实/正文片段的依据
CREATE TABLE IF NOT EXISTS novel_wiki_anchor (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT NOT NULL,
    page_id     BIGINT NOT NULL REFERENCES novel_wiki_page(id) ON DELETE CASCADE,
    field       VARCHAR(60) NOT NULL DEFAULT 'CANON',  -- attrs 键名或 CANON
    source_type VARCHAR(20) NOT NULL,                  -- AUTHOR / CHAPTER / FACT / PROPOSAL
    source_id   BIGINT,                                -- 章节 id / state_fact id / proposal id
    chapter_num INTEGER,
    quote       TEXT,
    confidence  NUMERIC(3,2),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_wiki_anchor_page ON novel_wiki_anchor(page_id, field);

-- ---------------------------------------------------------------------------
-- 知识库（重设计）
-- ---------------------------------------------------------------------------

-- 素材来源（原 knowledge_base 的升级形态）
CREATE TABLE IF NOT EXISTS novel_kb_source (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT NOT NULL,
    user_id     BIGINT,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    source_kind VARCHAR(20) NOT NULL DEFAULT 'UPLOAD', -- UPLOAD / MANUAL / URL
    file_name   VARCHAR(255),
    file_path   VARCHAR(1024),                         -- MinIO 对象路径
    checksum    VARCHAR(64),                           -- 秒传/幂等
    status      VARCHAR(20) NOT NULL DEFAULT 'READY',  -- PARSING / READY / FAILED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_kb_source_book ON novel_kb_source(book_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_source_file
    ON novel_kb_source(book_id, file_name) WHERE file_name IS NOT NULL;

-- 文本块：权威记录，可随时重建向量
CREATE TABLE IF NOT EXISTS novel_rag_chunk (
    id              BIGSERIAL PRIMARY KEY,
    book_id         BIGINT NOT NULL,
    source_id       BIGINT REFERENCES novel_kb_source(id) ON DELETE CASCADE,
    page_id         BIGINT REFERENCES novel_wiki_page(id) ON DELETE SET NULL,
    anchor_id       BIGINT REFERENCES novel_wiki_anchor(id) ON DELETE SET NULL,
    chunk_type      VARCHAR(20) NOT NULL,   -- SETTING / CHAPTER / REFERENCE
    chapter_num     INTEGER,
    seq             INTEGER NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    token_count     INTEGER,
    embedding_model VARCHAR(80),
    status          VARCHAR(20) NOT NULL DEFAULT 'READY', -- READY / STALE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_book_type ON novel_rag_chunk(book_id, chunk_type);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_source ON novel_rag_chunk(source_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_page ON novel_rag_chunk(page_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_rag_chunk_source_seq
    ON novel_rag_chunk(source_id, seq) WHERE source_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rag_chunk_chapter_seq
    ON novel_rag_chunk(book_id, chapter_num, seq)
    WHERE chunk_type = 'CHAPTER' AND chapter_num IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------------
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema='public' AND table_name LIKE 'novel_%'
--  ORDER BY table_name;
