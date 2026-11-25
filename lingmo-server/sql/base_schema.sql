-- =============================================================================
-- 基础业务表 DDL（sql/ 中原有的三个脚本都是增量脚本，缺这一层）
-- 依据 pojo/entity + resources/mapper/*.xml 的字段定义推导
-- 目标库：novel_system（主库），knowledge_base 表需导到 novel_knowledge_base
-- 全部使用 IF NOT EXISTS，可重复执行
-- =============================================================================

-- ---------------------------------------------------------------- 用户 / 作者
CREATE TABLE IF NOT EXISTS user_info (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    nick_name       VARCHAR(50),
    user_photo      VARCHAR(512),
    user_sex        SMALLINT     DEFAULT 0,   -- 0-男 1-女
    account_balance BIGINT       DEFAULT 0,
    status          SMALLINT     DEFAULT 0,   -- 0-正常
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS author_info (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL UNIQUE,
    invite_code    VARCHAR(64),
    pen_name       VARCHAR(50),
    tel_phone      VARCHAR(30),
    chat_account   VARCHAR(64),
    email          VARCHAR(100),
    work_direction SMALLINT    DEFAULT 0,      -- 0-男频 1-女频
    status         SMALLINT    DEFAULT 0,      -- 0-正常 1-封禁
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------- 书籍 / 分类 / 章节
CREATE TABLE IF NOT EXISTS book_category (
    id             BIGSERIAL PRIMARY KEY,
    work_direction SMALLINT    NOT NULL DEFAULT 0,
    name           VARCHAR(100) NOT NULL,
    sort           SMALLINT    DEFAULT 0,
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_direction_name UNIQUE (work_direction, name)
);

CREATE TABLE IF NOT EXISTS book_info (
    id                     BIGSERIAL PRIMARY KEY,
    work_direction         SMALLINT     DEFAULT 0,
    category_id            BIGINT,
    category_name          VARCHAR(100),
    pic_url                VARCHAR(512),
    book_name              VARCHAR(200) NOT NULL,
    author_id              BIGINT,
    author_name            VARCHAR(100),
    book_desc              TEXT,
    score                  SMALLINT     DEFAULT 0,
    book_status            SMALLINT     DEFAULT 0,   -- 0-连载中 1-已完结
    visit_count            BIGINT       DEFAULT 0,
    word_count             INTEGER      DEFAULT 0,
    comment_count          INTEGER      DEFAULT 0,
    last_chapter_id        BIGINT,
    last_chapter_name      VARCHAR(200),
    last_chapter_update_time TIMESTAMP,
    is_vip                 SMALLINT     DEFAULT 0,
    create_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tags                   VARCHAR(512),
    -- v2 管线：世界观唯一事实源（multi_agent.sql 里的 ALTER 已含在这里）
    world_setting          TEXT
);

CREATE TABLE IF NOT EXISTS book_chapter (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT      NOT NULL,
    chapter_num  INTEGER     NOT NULL,
    chapter_name VARCHAR(200),
    word_count   INTEGER     DEFAULT 0,
    is_vip       SMALLINT    DEFAULT 0,
    create_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS book_content (
    id          BIGSERIAL PRIMARY KEY,
    chapter_id  BIGINT NOT NULL,
    content     TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------- 草稿 / 会话 / 书架
CREATE TABLE IF NOT EXISTS create_book (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT,
    title          VARCHAR(200),
    number         INTEGER,
    word_count     INTEGER   DEFAULT 0,
    status         SMALLINT  DEFAULT 0,
    content        JSONB,                -- JsonbTypeHandler
    author_id      BIGINT,
    tags           VARCHAR(512),
    notes          TEXT,
    published_time TIMESTAMP,
    created_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 注意：entity 的 @TableName 是 chat_history；character_cards.sql 里建的是
-- conversation_message_blocks（列名相同），两个表名都要存在才不会踩空。
CREATE TABLE IF NOT EXISTS chat_history (
    id               BIGSERIAL PRIMARY KEY,
    book_id          BIGINT      NOT NULL,
    block_data_jsonb JSONB       NOT NULL,
    message_count    INTEGER     NOT NULL,
    start_timestamp  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    end_timestamp    TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_bookshelf (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT    NOT NULL,
    book_id       BIGINT    NOT NULL,
    pre_content_id BIGINT,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bookshelf_user_book UNIQUE (user_id, book_id)
);

-- ---------------------------------------------------------------- 常用索引
CREATE INDEX IF NOT EXISTS idx_book_info_author    ON book_info(author_id);
CREATE INDEX IF NOT EXISTS idx_book_info_category  ON book_info(category_id);
CREATE INDEX IF NOT EXISTS idx_book_chapter_book   ON book_chapter(book_id);
CREATE INDEX IF NOT EXISTS idx_book_content_chapter ON book_content(chapter_id);
CREATE INDEX IF NOT EXISTS idx_create_book_book    ON create_book(book_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_book   ON chat_history(book_id);
CREATE INDEX IF NOT EXISTS idx_user_bookshelf_user ON user_bookshelf(user_id);

-- ---------------------------------------------------------------- 基础分类数据
INSERT INTO book_category (work_direction, name, sort) VALUES
    (0, '玄幻', 1), (0, '都市', 2), (0, '仙侠', 3), (0, '科幻', 4), (0, '历史', 5),
    (1, '古代言情', 1), (1, '现代言情', 2), (1, '穿越奇情', 3), (1, '宫斗宅斗', 4)
ON CONFLICT (work_direction, name) DO NOTHING;

-- =============================================================================
-- 以下这段请导到 novel_knowledge_base（知识库数据源）
-- 直接用 psql -d novel_knowledge_base -f 本文件也会建，主库里多一张无用的表而已
-- =============================================================================
CREATE TABLE IF NOT EXISTS knowledge_base (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    name         VARCHAR(200),
    description  TEXT,
    document_ids JSONB,                  -- entity: documentIds -> MP 生成 SQL 用 document_ids
    create_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_book ON knowledge_base(book_id);
