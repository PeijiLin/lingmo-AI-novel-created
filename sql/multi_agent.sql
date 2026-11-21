-- ============================================================================
-- å¤šæ™ºèƒ½ä½“åˆ›ä½œæµæ°´çº¿ DDLï¼ˆnovel_system åº“ï¼‰
-- å¯¹åº”è®¾è®¡æ–‡æ¡£ï¼šdocs/MULTI_AGENT_CREATION_DESIGN.md
-- é˜¶æ®µ1ï¼šcreation_task / creation_stage_artifactï¼ˆä»»åŠ¡ä¸Žé˜¶æ®µäº§ç‰©è½åº“ + token åŸ‹ç‚¹ï¼‰
-- é˜¶æ®µ2ï¼šWorld State è´¦æœ¬å››å¼ è¡¨ + book_info.world_settingï¼ˆæœåŠ¡ç«¯ä¸–ç•Œè§‚å”¯ä¸€äº‹å®žæºï¼‰
-- ============================================================================

-- ---------------------------------------------------------------------------
-- é˜¶æ®µ1ï¼šåˆ›ä½œä»»åŠ¡ä¸Žé˜¶æ®µäº§ç‰©
-- ---------------------------------------------------------------------------

-- åˆ›ä½œä»»åŠ¡ï¼ˆä¸€æ¬¡ç®¡é“æ‰§è¡Œçš„è®°å½•ï¼Œç»Ÿè®¡ token/è€—æ—¶ï¼Œä¸ºæˆæœ¬é¢æ¿æ‰“åº•ï¼‰
CREATE TABLE IF NOT EXISTS creation_task (
    id            BIGSERIAL PRIMARY KEY,
    book_id       BIGINT,
    chapter_id    BIGINT,
    mode          VARCHAR(20)  NOT NULL,                    -- FAST / STANDARD / REFINED / PRECISE
    status        VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',  -- PENDING / RUNNING / COMPLETED / FAILED
    current_stage VARCHAR(30),                             -- å½“å‰æŽ¨è¿›åˆ°çš„é˜¶æ®µï¼ˆæ–­ç‚¹ç»­è·‘é¢„ç•™ï¼‰
    degraded      BOOLEAN      NOT NULL DEFAULT FALSE,     -- æ˜¯å¦å‘ç”Ÿé™çº§
    total_tokens  INTEGER,                                 -- å…¨ç¨‹ LLM token ç”¨é‡
    elapsed_ms    BIGINT,                                  -- å…¨ç¨‹è€—æ—¶ï¼ˆæ¯«ç§’ï¼‰
    error         TEXT,                                    -- å¤±è´¥åŽŸå› 
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- å¼‚æ­¥ä»»åŠ¡å…¥å‚ï¼ˆå´©æºƒé‡å¯åŽé‡å»ºç®¡é“å…¥å‚ï¼›å·²å»ºè¡¨çš„çŽ¯å¢ƒæ‰§è¡Œæ­¤å¥å³å¯å¢žé‡å‡çº§ï¼‰
ALTER TABLE creation_task ADD COLUMN IF NOT EXISTS params JSONB;

CREATE INDEX IF NOT EXISTS idx_creation_task_book   ON creation_task(book_id);
CREATE INDEX IF NOT EXISTS idx_creation_task_status ON creation_task(status);

-- é˜¶æ®µäº§ç‰©ï¼ˆæ¯é˜¶æ®µç»“æž„åŒ–äº§ç‰©å®žæ—¶è½åº“ï¼Œå¼‚å¸¸ä¸­æ–­åŽäº§ç‰©ä»åœ¨ï¼Œæ”¯æŒåŸºäºŽ last artifact ç»­è·‘ï¼‰
CREATE TABLE IF NOT EXISTS creation_stage_artifact (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    stage      VARCHAR(30)  NOT NULL,               -- PLANNING / WRITING / CRITIC / POLISHING
    round      INTEGER      NOT NULL DEFAULT 1,     -- é‡å†™è½®æ¬¡ï¼ˆé¦–è½®=1ï¼‰
    artifact   JSONB        NOT NULL,               -- ç»“æž„åŒ–äº§ç‰©ï¼šoutline / draft / review / polished
    tokens     INTEGER,                             -- æœ¬é˜¶æ®µ token ç”¨é‡
    elapsed_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_task ON creation_stage_artifact(task_id);

-- ---------------------------------------------------------------------------
-- é˜¶æ®µ2ï¼šWorld State è´¦æœ¬ï¼ˆå…±äº«ä¸Šä¸‹æ–‡ï¼Œå¤š Agent ä¸€è‡´æ€§é è´¦æœ¬è€Œéžæ¨¡åž‹è®°æ€§ï¼‰
-- ---------------------------------------------------------------------------

-- æ»šåŠ¨å‰æƒ…æ‘˜è¦ï¼ˆbook çº§å”¯ä¸€ï¼ŒæŽ§åˆ¶åœ¨ 2000 å­—å†…ï¼‰
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

-- äººç‰©åŠ¨æ€çŠ¶æ€ï¼ˆè§’è‰²å¡æ˜¯é™æ€è®¾å®šï¼Œè¿™å¼ æ˜¯éšå‰§æƒ…æŽ¨è¿›çš„å½“å‰æ€ï¼›book+character å”¯ä¸€ï¼‰
CREATE TABLE IF NOT EXISTS novel_character_state (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    chapter_id   BIGINT,                             -- çŠ¶æ€å¿«ç…§è‡³å“ªç« 
    state        JSONB  NOT NULL,                    -- {ä½ç½®, å¿ƒæ€, æ–°èŽ·çŸ¥ä¿¡æ¯, æŒæœ‰ç‰©å“, å—ä¼¤/èƒ½åŠ›å˜åŒ–}
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_char_state UNIQUE (book_id, character_id)
);

-- ä¸–ç•Œè§‚å”¯ä¸€äº‹å®žæºï¼šbook_info å¢žåŠ  world_setting åˆ—ï¼ˆå‰ç«¯ä»å¯ä¼ ï¼Œä»…ä½œè¦†ç›–æç¤ºï¼‰
ALTER TABLE book_info ADD COLUMN IF NOT EXISTS world_setting TEXT;

-- ---------------------------------------------------------------------------
-- é˜¶æ®µ4ï¼šKafka å¼‚æ­¥ä»»åŠ¡ä¾èµ–ï¼ˆå¹‚ç­‰æ …æ ç”± creation_task.status æ‰¿æ‹…ï¼Œæ— éœ€é¢å¤–è¡¨ï¼‰
-- topic ç”±åº”ç”¨å¯åŠ¨æ—¶è‡ªåŠ¨åˆ›å»ºï¼ˆKafkaConfigï¼‰ï¼š
--   novel.creation.task      æ•´ç« /æ‰¹é‡ç”Ÿæˆå¼‚æ­¥ä»»åŠ¡
--   novel.chapter.finalized  ç« èŠ‚å†…å®¹å®šç¨¿ï¼ˆæ‘˜è¦æ»šåŠ¨ + äººç‰©çŠ¶æ€æŠ½å–ï¼‰
-- ---------------------------------------------------------------------------

-- ³åÍ»ÖÙ²ÃÕË±¾£¨v2.0 Éè¼Æ 6.4£º³åÍ»²»¾²Ä¬¸²¸Ç£¬Ë«ÏòÁôºÛ£©
-- R1 ×ÔÈ»ÑÝ»¯£¨Èç ALIVE->DEAD£©£º×Ô¶¯ÉúÐ§ + AUTO_APPLIED ÁôºÛ
-- R3 ËÀÍö»ØÍË£¨DEAD->ALIVE£¬ÒÉËÆ¸´»î/Ð´×÷´íÎó£©£º¾É×´Ì¬±£Áô²»ÈëÕË£¬PENDING_REVIEW ´ý×÷ÕßÖÙ²Ã
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

-- ÊÂ¼þÊ±¼äÏß£¨v2.0 Éè¼Æ 6.3 ÈýÊ±¼äÖáÖ® T_story£º¹ÊÊÂÊ±¼ä°´"ÊéÄÚÌìÊýÖá"¹éÒ»»¯£©
-- LLM Ö»Êä³ö½á¹¹»¯Æ«ÒÆ£¨ÊýÖµ+µ¥Î»+²ÎÕÕ£©£¬´úÂë×öÈÕÀúÔËËã£¨¹«Àí P3 ·Ö¹¤£¬»Ã¾õ¸ß·¢Çø²»ÈÃ LLM Ëã£©
-- day_index£ºÊéÄÚµÚ N Ìì£¨ÕûÊýÖá£©£»null = Ê±¼ä²»Ã÷£¨UNKNOWN/ABSOLUTE_LABEL£©£¬Ê±ÐòÐ£Ñé½µ¼¶ÎªÆ«Ðò
CREATE TABLE IF NOT EXISTS novel_event_timeline (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    scene_no       INTEGER,
    narrative_mode VARCHAR(20) NOT NULL DEFAULT 'NORMAL',   -- NORMAL/FLASHBACK/DREAM/STORY_IN_STORY/UNRELIABLE£¨C4£©
    time_kind      VARCHAR(30) NOT NULL,     -- EXACT / RELATIVE_RESOLVED / FUZZY / UNKNOWN / ABSOLUTE_LABEL
    day_index      INTEGER,                  -- T_story ¹éÒ»»¯ÌìÊý£¨null=²»¶¨£©
    raw_time_text  TEXT,                     -- Ô­ÎÄÊ±¼ä±íÊö£¨"ÈýÌìºóÒ¹Íí"£©
    event_desc     TEXT NOT NULL,
    location       VARCHAR(200),
    participants   JSONB,
    source_chapter BIGINT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_event_timeline_book ON novel_event_timeline(book_id, chapter_id);

-- ÈËÎï×´Ì¬ÊÂÊµÕË±¾£¨v2.0 Éè¼Æ 6.3£ºÈýÊ±¼äÖáÖ® T_narr ÉúÐ§ÕÂÇø¼ä£¬ÈíÊ§Ð§²»ÎïÀíÉ¾³ý£©
-- ÐÂÊÂÊµÈëÕËÊ±½«¾ÉÊÂÊµ valid_to_chapter ÖÃÎªÐÂÇø¼äÇ°ÕÂ£»µ¹Ðð/»ØËÝ°´"»ØÒäÄ¿±êÊ±¿Ì"È¡¿ìÕÕ¡£
-- µ±Ç°Ì¬±í novel_character_state ±£ÁôÎªÐÔÄÜÍ¶Ó°£¬±¾±íÎª¿É»ØËÝÈ¨ÍþÕË±¾£¨¿É´Ó±¾±íÖØ½¨Í¶Ó°£©¡£
CREATE TABLE IF NOT EXISTS novel_state_fact (
    id                 BIGSERIAL PRIMARY KEY,
    book_id            BIGINT NOT NULL,
    character_id       BIGINT NOT NULL,
    source_chapter     BIGINT,                 -- ¿ìÕÕÀ´Ô´ÕÂ£¨T_narr£©
    valid_from_chapter BIGINT NOT NULL,
    valid_to_chapter   BIGINT,                 -- null = µ±Ç°ÓÐÐ§£¨ÈíÊ§Ð§ÓÀ²»ÎïÀíÉ¾³ý£©
    state              JSONB NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_state_fact_lookup ON novel_state_fact(book_id, character_id, valid_from_chapter);

-- ·ü±ÊÖªÇé¼¯ºÏ£¨v2.0 M2-C5£ºÃØÃÜµÄ±ÕÊÀ½çÅÐ¶¨¡ª¡ª²éÑ¯"Ë­ÖªµÀ"£¬²»Çî¾Ù"Ë­²»ÖªµÀ"£©
-- whoKnows ·Ç¿Õ¼´ÊÓÎª"´øÖªÇé±ß½çµÄÊÂÊµ"£»secretKey ÎªÕýÎÄ¿É¼ìË÷¶Ì¹Ø¼ü´Ê£¨RC-05 ÎÄ±¾¶¨Î»ÓÃ£©
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS who_knows JSONB;
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS secret_key VARCHAR(200);

-- ´ýÖ¤Êµ¶ÏÑÔÇø£¨v2.0 M2-C6£º·Ç FACT ¶ÏÑÔ¸ôÀë¡ª¡ªÉý¸ñ >=2 ¶ÀÁ¢Ãªµã£¬30 ÕÂÎ´Ö¤Êµ¹ýÆÚ£©
CREATE TABLE IF NOT EXISTS novel_pending_assertion (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    character_id   BIGINT,
    assertion_type VARCHAR(20) NOT NULL,    -- LIE / HEARSAY / HYPOTHETICAL / DREAM
    payload        JSONB NOT NULL,          -- ³éÈ¡¿ìÕÕÔ­ÎÄ£¨Éý¸ñÊ±×÷Îª FACT ÒÀ¾Ý£©
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING / CONFIRMED / EXPIRED / REJECTED
    confirmations  INTEGER NOT NULL DEFAULT 0,
    expires_chapter BIGINT,                 -- chapterId + 30£»¹ýÆÚÇåÀí½ø¹éµµ£¨²»ÎïÀíÉ¾³ý£©
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_pending_assertion_book ON novel_pending_assertion(book_id, status);

-- ¹æÔòÎó±¨»íÃâ£¨v2.0 M3-D6 Îó±¨±Õ»·£º×÷Õß±ê¼ÇÎó±¨ ¡ú »íÃâ°×Ãûµ¥ ¡ú »Ø¹é¼¯£©
-- »íÃâ¼´¸Ã¹æÔò¶Ô±¾Êé¾²Ä¬£»»íÃâÇåµ¥Í¬Ê±ÊÇ¹æÔòµ÷ÕûÊ±±ØÐëÖØÅÜµÄ»Ø¹é¼¯Êý¾ÝÔ´
CREATE TABLE IF NOT EXISTS novel_rule_exemption (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    rule_id    VARCHAR(30) NOT NULL,
    reason     TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rule_exemption UNIQUE (book_id, rule_id)
);
CREATE INDEX IF NOT EXISTS idx_rule_exemption_book ON novel_rule_exemption(book_id);

-- °²È«Éó¼ÆÕË±¾£¨v2.0 Éè¼Æ 7.1/7.4£ºÈý²ã·ÀÏßÈ«Á¿ÁôÖ¤£¬À¹½ØÂÊ/ÎóÉ±ÂÊË«Ö¸±êÊý¾ÝÔ´£©
-- ÈýÒªËØ£ºË­£¨book/user£©¡¢ÒòºÎ£¨layer+category+detail£©¡¢ÈçºÎ´¦ÖÃ£¨action£©
CREATE TABLE IF NOT EXISTS novel_safety_log (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT,
    layer      VARCHAR(20) NOT NULL,     -- PRE / STREAM / POST£¨Ç°ÖÃ/ÖÐÖÃ/ºóÖÃ£©
    category   VARCHAR(40) NOT NULL,     -- PROMPT_INJECTION / SUSPECTED_INJECTION / CONSTRAINT_VIOLATION / ...
    action     VARCHAR(20) NOT NULL,     -- REJECT / FLAG / REWRITE / PASS
    detail     TEXT,                     -- ×ªÒåºóµÄÁôÖ¤£¨¿ØÖÆ·û°þÀë + ³¤¶È½Ø¶Ï£¬·ÀÈÕÖ¾¶þ´Î×¢Èë£©
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_safety_log_book ON novel_safety_log(book_id, layer);
