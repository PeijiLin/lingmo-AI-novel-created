-- ============================================================================
-- å¤šæ™ºèƒ½ä½“åˆ›ä½œæµæ°´çº¿ DDLï¼ˆnovel_system åº“ï¼‰
-- å¯¹åº”è®¾è®¡æ–‡æ¡£ï¼šdocs/MULTI_AGENT_CREATION_DESIGN.md
-- é˜¶æ®µ1ï¼šcreation_task / creation_stage_artifactï¼ˆä»»åŠ¡ä¸é˜¶æ®µäº§ç‰©è½åº“ + token åŸ‹ç‚¹ï¼‰
-- é˜¶æ®µ2ï¼šWorld State è´¦æœ¬å››å¼ è¡¨ + book_info.world_settingï¼ˆæœåŠ¡ç«¯ä¸–ç•Œè§‚å”¯ä¸€äº‹å®æºï¼‰
-- ============================================================================

-- ---------------------------------------------------------------------------
-- é˜¶æ®µ1ï¼šåˆ›ä½œä»»åŠ¡ä¸é˜¶æ®µäº§ç‰©
-- ---------------------------------------------------------------------------

-- åˆ›ä½œä»»åŠ¡ï¼ˆä¸€æ¬¡ç®¡é“æ‰§è¡Œçš„è®°å½•ï¼Œç»Ÿè®¡ token/è€—æ—¶ï¼Œä¸ºæˆæœ¬é¢æ¿æ‰“åº•ï¼‰
CREATE TABLE IF NOT EXISTS creation_task (
    id            BIGSERIAL PRIMARY KEY,
    book_id       BIGINT,
    chapter_id    BIGINT,
    mode          VARCHAR(20)  NOT NULL,                    -- FAST / STANDARD / REFINED / PRECISE
    status        VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',  -- PENDING / RUNNING / COMPLETED / FAILED
    current_stage VARCHAR(30),                             -- å½“å‰æ¨è¿›åˆ°çš„é˜¶æ®µï¼ˆæ–­ç‚¹ç»­è·‘é¢„ç•™ï¼‰
    degraded      BOOLEAN      NOT NULL DEFAULT FALSE,     -- æ˜¯å¦å‘ç”Ÿé™çº§
    total_tokens  INTEGER,                                 -- å…¨ç¨‹ LLM token ç”¨é‡
    elapsed_ms    BIGINT,                                  -- å…¨ç¨‹è€—æ—¶ï¼ˆæ¯«ç§’ï¼‰
    error         TEXT,                                    -- å¤±è´¥åŸå› 
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- å¼‚æ­¥ä»»åŠ¡å…¥å‚ï¼ˆå´©æºƒé‡å¯åé‡å»ºç®¡é“å…¥å‚ï¼›å·²å»ºè¡¨çš„ç¯å¢ƒæ‰§è¡Œæ­¤å¥å³å¯å¢é‡å‡çº§ï¼‰
ALTER TABLE creation_task ADD COLUMN IF NOT EXISTS params JSONB;

CREATE INDEX IF NOT EXISTS idx_creation_task_book   ON creation_task(book_id);
CREATE INDEX IF NOT EXISTS idx_creation_task_status ON creation_task(status);

-- é˜¶æ®µäº§ç‰©ï¼ˆæ¯é˜¶æ®µç»“æ„åŒ–äº§ç‰©å®æ—¶è½åº“ï¼Œå¼‚å¸¸ä¸­æ–­åäº§ç‰©ä»åœ¨ï¼Œæ”¯æŒåŸºäº last artifact ç»­è·‘ï¼‰
CREATE TABLE IF NOT EXISTS creation_stage_artifact (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    stage      VARCHAR(30)  NOT NULL,               -- PLANNING / WRITING / CRITIC / POLISHING
    round      INTEGER      NOT NULL DEFAULT 1,     -- é‡å†™è½®æ¬¡ï¼ˆé¦–è½®=1ï¼‰
    artifact   JSONB        NOT NULL,               -- ç»“æ„åŒ–äº§ç‰©ï¼šoutline / draft / review / polished
    tokens     INTEGER,                             -- æœ¬é˜¶æ®µ token ç”¨é‡
    elapsed_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_task ON creation_stage_artifact(task_id);

-- ---------------------------------------------------------------------------
-- é˜¶æ®µ2ï¼šWorld State è´¦æœ¬ï¼ˆå…±äº«ä¸Šä¸‹æ–‡ï¼Œå¤š Agent ä¸€è‡´æ€§é è´¦æœ¬è€Œéæ¨¡å‹è®°æ€§ï¼‰
-- ---------------------------------------------------------------------------

-- æ»šåŠ¨å‰æƒ…æ‘˜è¦ï¼ˆbook çº§å”¯ä¸€ï¼Œæ§åˆ¶åœ¨ 2000 å­—å†…ï¼‰
CREATE TABLE IF NOT EXISTS novel_story_digest (
    book_id         BIGINT PRIMARY KEY,
    summary         TEXT NOT NULL,
    last_chapter_id BIGINT,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ç« èŠ‚å¤§çº²ï¼ˆPlanner äº§ç‰©ï¼Œå«ä¿®è®¢ç‰ˆæœ¬ï¼‰
CREATE TABLE IF NOT EXISTS novel_chapter_outline (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    chapter_id BIGINT,
    outline    JSONB  NOT NULL,                     -- ChapterOutline åºåˆ—åŒ–
    version    INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outline_book ON novel_chapter_outline(book_id);

-- ä¼ç¬”è´¦æœ¬ï¼ˆæ ¸å¿ƒèµ„äº§ï¼šOPEN / RESOLVED / ABANDONEDï¼‰
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

-- äººç‰©åŠ¨æ€çŠ¶æ€ï¼ˆè§’è‰²å¡æ˜¯é™æ€è®¾å®šï¼Œè¿™å¼ æ˜¯éšå‰§æƒ…æ¨è¿›çš„å½“å‰æ€ï¼›book+character å”¯ä¸€ï¼‰
CREATE TABLE IF NOT EXISTS novel_character_state (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    chapter_id   BIGINT,                             -- çŠ¶æ€å¿«ç…§è‡³å“ªç« 
    state        JSONB  NOT NULL,                    -- {ä½ç½®, å¿ƒæ€, æ–°è·çŸ¥ä¿¡æ¯, æŒæœ‰ç‰©å“, å—ä¼¤/èƒ½åŠ›å˜åŒ–}
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_char_state UNIQUE (book_id, character_id)
);

-- ä¸–ç•Œè§‚å”¯ä¸€äº‹å®æºï¼šbook_info å¢åŠ  world_setting åˆ—ï¼ˆå‰ç«¯ä»å¯ä¼ ï¼Œä»…ä½œè¦†ç›–æç¤ºï¼‰
ALTER TABLE book_info ADD COLUMN IF NOT EXISTS world_setting TEXT;

-- ---------------------------------------------------------------------------
-- é˜¶æ®µ4ï¼šKafka å¼‚æ­¥ä»»åŠ¡ä¾èµ–ï¼ˆå¹‚ç­‰æ …æ ç”± creation_task.status æ‰¿æ‹…ï¼Œæ— éœ€é¢å¤–è¡¨ï¼‰
-- topic ç”±åº”ç”¨å¯åŠ¨æ—¶è‡ªåŠ¨åˆ›å»ºï¼ˆKafkaConfigï¼‰ï¼š
--   novel.creation.task      æ•´ç« /æ‰¹é‡ç”Ÿæˆå¼‚æ­¥ä»»åŠ¡
--   novel.chapter.finalized  ç« èŠ‚å†…å®¹å®šç¨¿ï¼ˆæ‘˜è¦æ»šåŠ¨ + äººç‰©çŠ¶æ€æŠ½å–ï¼‰
-- ---------------------------------------------------------------------------

-- ³åÍ»ÖÙ²ÃÕË±¾£¨v2.0 Éè¼Æ 6.4£º³åÍ»²»¾²Ä¬¸²¸Ç£¬Ë«ÏòÁôºÛ£©
-- R1 ×ÔÈ»Ñİ»¯£¨Èç ALIVE->DEAD£©£º×Ô¶¯ÉúĞ§ + AUTO_APPLIED ÁôºÛ
-- R3 ËÀÍö»ØÍË£¨DEAD->ALIVE£¬ÒÉËÆ¸´»î/Ğ´×÷´íÎó£©£º¾É×´Ì¬±£Áô²»ÈëÕË£¬PENDING_REVIEW ´ı×÷ÕßÖÙ²Ã
CREATE TABLE IF NOT EXISTS novel_wiki_conflict (
    id            BIGSERIAL PRIMARY KEY,
    book_id       BIGINT NOT NULL,
    chapter_id    BIGINT,
    character_id  BIGINT,
    conflict_kind VARCHAR(30) NOT NULL,     -- STATE_EVOLVE / RESURRECTION_REVIEW
    old_value     VARCHAR(100),
    new_value     VARCHAR(100),
    status        VARCHAR(30) NOT NULL,     -- AUTO_APPLIED / PENDING_REVIEW / RESOLVED / REJECTED
    note          TEXT,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_wiki_conflict_book_status ON novel_wiki_conflict(book_id, status);

-- ÊÂ¼şÊ±¼äÏß£¨v2.0 Éè¼Æ 6.3 ÈıÊ±¼äÖáÖ® T_story£º¹ÊÊÂÊ±¼ä°´"ÊéÄÚÌìÊıÖá"¹éÒ»»¯£©
-- LLM Ö»Êä³ö½á¹¹»¯Æ«ÒÆ£¨ÊıÖµ+µ¥Î»+²ÎÕÕ£©£¬´úÂë×öÈÕÀúÔËËã£¨¹«Àí P3 ·Ö¹¤£¬»Ã¾õ¸ß·¢Çø²»ÈÃ LLM Ëã£©
-- day_index£ºÊéÄÚµÚ N Ìì£¨ÕûÊıÖá£©£»null = Ê±¼ä²»Ã÷£¨UNKNOWN/ABSOLUTE_LABEL£©£¬Ê±ĞòĞ£Ñé½µ¼¶ÎªÆ«Ğò
CREATE TABLE IF NOT EXISTS novel_event_timeline (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    scene_no       INTEGER,
    narrative_mode VARCHAR(20) NOT NULL DEFAULT 'NORMAL',   -- NORMAL/FLASHBACK/DREAM/STORY_IN_STORY/UNRELIABLE£¨C4£©
    time_kind      VARCHAR(30) NOT NULL,     -- EXACT / RELATIVE_RESOLVED / FUZZY / UNKNOWN / ABSOLUTE_LABEL
    day_index      INTEGER,                  -- T_story ¹éÒ»»¯ÌìÊı£¨null=²»¶¨£©
    raw_time_text  TEXT,                     -- Ô­ÎÄÊ±¼ä±íÊö£¨"ÈıÌìºóÒ¹Íí"£©
    event_desc     TEXT NOT NULL,
    location       VARCHAR(200),
    participants   JSONB,
    source_chapter BIGINT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_event_timeline_book ON novel_event_timeline(book_id, chapter_id);

-- ÈËÎï×´Ì¬ÊÂÊµÕË±¾£¨v2.0 Éè¼Æ 6.3£ºÈıÊ±¼äÖáÖ® T_narr ÉúĞ§ÕÂÇø¼ä£¬ÈíÊ§Ğ§²»ÎïÀíÉ¾³ı£©
-- ĞÂÊÂÊµÈëÕËÊ±½«¾ÉÊÂÊµ valid_to_chapter ÖÃÎªĞÂÇø¼äÇ°ÕÂ£»µ¹Ğğ/»ØËİ°´"»ØÒäÄ¿±êÊ±¿Ì"È¡¿ìÕÕ¡£
-- µ±Ç°Ì¬±í novel_character_state ±£ÁôÎªĞÔÄÜÍ¶Ó°£¬±¾±íÎª¿É»ØËİÈ¨ÍşÕË±¾£¨¿É´Ó±¾±íÖØ½¨Í¶Ó°£©¡£
CREATE TABLE IF NOT EXISTS novel_state_fact (
    id                 BIGSERIAL PRIMARY KEY,
    book_id            BIGINT NOT NULL,
    character_id       BIGINT NOT NULL,
    source_chapter     BIGINT,                 -- ¿ìÕÕÀ´Ô´ÕÂ£¨T_narr£©
    valid_from_chapter BIGINT NOT NULL,
    valid_to_chapter   BIGINT,                 -- null = µ±Ç°ÓĞĞ§£¨ÈíÊ§Ğ§ÓÀ²»ÎïÀíÉ¾³ı£©
    state              JSONB NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_state_fact_lookup ON novel_state_fact(book_id, character_id, valid_from_chapter);
