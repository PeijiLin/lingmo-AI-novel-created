-- ============================================================================
-- 澶氭櫤鑳戒綋鍒涗綔娴佹按绾? DDL锛坣ovel_system 搴擄級
-- 瀵瑰簲璁捐鏂囨。锛歞ocs/MULTI_AGENT_CREATION_DESIGN.md
-- 闃舵1锛歝reation_task / creation_stage_artifact锛堜换鍔′笌闃舵浜х墿钀藉簱 + token 鍩嬬偣锛?
-- 闃舵2锛歐orld State 璐︽湰鍥涘紶琛? + book_info.world_setting锛堟湇鍔＄涓栫晫瑙傚敮涓?浜嬪疄婧愶級
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 闃舵1锛氬垱浣滀换鍔′笌闃舵浜х墿
-- ---------------------------------------------------------------------------

-- 鍒涗綔浠诲姟锛堜竴娆＄閬撴墽琛岀殑璁板綍锛岀粺璁? token/鑰楁椂锛屼负鎴愭湰闈㈡澘鎵撳簳锛?
CREATE TABLE IF NOT EXISTS creation_task (
    id            BIGSERIAL PRIMARY KEY,
    book_id       BIGINT,
    chapter_id    BIGINT,
    mode          VARCHAR(20)  NOT NULL,                    -- FAST / STANDARD / REFINED / PRECISE
    status        VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',  -- PENDING / RUNNING / COMPLETED / FAILED
    current_stage VARCHAR(30),                             -- 褰撳墠鎺ㄨ繘鍒扮殑闃舵锛堟柇鐐圭画璺戦鐣欙級
    degraded      BOOLEAN      NOT NULL DEFAULT FALSE,     -- 鏄惁鍙戠敓闄嶇骇
    total_tokens  INTEGER,                                 -- 鍏ㄧ▼ LLM token 鐢ㄩ噺
    elapsed_ms    BIGINT,                                  -- 鍏ㄧ▼鑰楁椂锛堟绉掞級
    error         TEXT,                                    -- 澶辫触鍘熷洜
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 寮傛浠诲姟鍏ュ弬锛堝穿婧冮噸鍚悗閲嶅缓绠￠亾鍏ュ弬锛涘凡寤鸿〃鐨勭幆澧冩墽琛屾鍙ュ嵆鍙閲忓崌绾э級
ALTER TABLE creation_task ADD COLUMN IF NOT EXISTS params JSONB;

CREATE INDEX IF NOT EXISTS idx_creation_task_book   ON creation_task(book_id);
CREATE INDEX IF NOT EXISTS idx_creation_task_status ON creation_task(status);

-- 闃舵浜х墿锛堟瘡闃舵缁撴瀯鍖栦骇鐗╁疄鏃惰惤搴擄紝寮傚父涓柇鍚庝骇鐗╀粛鍦紝鏀寔鍩轰簬 last artifact 缁窇锛?
CREATE TABLE IF NOT EXISTS creation_stage_artifact (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    stage      VARCHAR(30)  NOT NULL,               -- PLANNING / WRITING / CRITIC / POLISHING
    round      INTEGER      NOT NULL DEFAULT 1,     -- 閲嶅啓杞锛堥杞?=1锛?
    artifact   JSONB        NOT NULL,               -- 缁撴瀯鍖栦骇鐗╋細outline / draft / review / polished
    tokens     INTEGER,                             -- 鏈樁娈? token 鐢ㄩ噺
    elapsed_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_task ON creation_stage_artifact(task_id);

-- ---------------------------------------------------------------------------
-- 闃舵2锛歐orld State 璐︽湰锛堝叡浜笂涓嬫枃锛屽 Agent 涓?鑷存?ч潬璐︽湰鑰岄潪妯″瀷璁版?э級
-- ---------------------------------------------------------------------------

-- 婊氬姩鍓嶆儏鎽樿锛坆ook 绾у敮涓?锛屾帶鍒跺湪 2000 瀛楀唴锛?
CREATE TABLE IF NOT EXISTS novel_story_digest (
    book_id         BIGINT PRIMARY KEY,
    summary         TEXT NOT NULL,
    last_chapter_id BIGINT,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 绔犺妭澶х翰锛圥lanner 浜х墿锛屽惈淇鐗堟湰锛?
CREATE TABLE IF NOT EXISTS novel_chapter_outline (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    chapter_id BIGINT,
    outline    JSONB  NOT NULL,                     -- ChapterOutline 搴忓垪鍖?
    version    INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outline_book ON novel_chapter_outline(book_id);

-- 浼忕瑪璐︽湰锛堟牳蹇冭祫浜э細OPEN / RESOLVED / ABANDONED锛?
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

-- 浜虹墿鍔ㄦ?佺姸鎬侊紙瑙掕壊鍗℃槸闈欐?佽瀹氾紝杩欏紶鏄殢鍓ф儏鎺ㄨ繘鐨勫綋鍓嶆?侊紱book+character 鍞竴锛?
CREATE TABLE IF NOT EXISTS novel_character_state (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    chapter_id   BIGINT,                             -- 鐘舵?佸揩鐓ц嚦鍝珷
    state        JSONB  NOT NULL,                    -- {浣嶇疆, 蹇冩??, 鏂拌幏鐭ヤ俊鎭?, 鎸佹湁鐗╁搧, 鍙椾激/鑳藉姏鍙樺寲}
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_char_state UNIQUE (book_id, character_id)
);

-- 涓栫晫瑙傚敮涓?浜嬪疄婧愶細book_info 澧炲姞 world_setting 鍒楋紙鍓嶇浠嶅彲浼狅紝浠呬綔瑕嗙洊鎻愮ず锛?
ALTER TABLE book_info ADD COLUMN IF NOT EXISTS world_setting TEXT;

-- ---------------------------------------------------------------------------
-- 闃舵4锛欿afka 寮傛浠诲姟渚濊禆锛堝箓绛夋爡鏍忕敱 creation_task.status 鎵挎媴锛屾棤闇?棰濆琛級
-- topic 鐢卞簲鐢ㄥ惎鍔ㄦ椂鑷姩鍒涘缓锛圞afkaConfig锛夛細
--   novel.creation.task      鏁寸珷/鎵归噺鐢熸垚寮傛浠诲姟
--   novel.chapter.finalized  绔犺妭鍐呭瀹氱ǹ锛堟憳瑕佹粴鍔? + 浜虹墿鐘舵?佹娊鍙栵級
-- ---------------------------------------------------------------------------

-- 冲突仲裁账本（v2.0 设计 6.4：冲突不静默覆盖，双向留痕）
-- R1 自然演化（如 ALIVE->DEAD）：自动生效 + AUTO_APPLIED 留痕
-- R3 死亡回退（DEAD->ALIVE，疑似复活/写作错误）：旧状态保留不入账，PENDING_REVIEW 待作者仲裁
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

-- 事件时间线（v2.0 设计 6.3 三时间轴之 T_story：故事时间按"书内天数轴"归一化）
-- LLM 只输出结构化偏移（数值+单位+参照），代码做日历运算（公理 P3 分工，幻觉高发区不让 LLM 算）
-- day_index：书内第 N 天（整数轴）；null = 时间不明（UNKNOWN/ABSOLUTE_LABEL），时序校验降级为偏序
CREATE TABLE IF NOT EXISTS novel_event_timeline (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    scene_no       INTEGER,
    narrative_mode VARCHAR(20) NOT NULL DEFAULT 'NORMAL',   -- NORMAL/FLASHBACK/DREAM/STORY_IN_STORY/UNRELIABLE（C4）
    time_kind      VARCHAR(30) NOT NULL,     -- EXACT / RELATIVE_RESOLVED / FUZZY / UNKNOWN / ABSOLUTE_LABEL
    day_index      INTEGER,                  -- T_story 归一化天数（null=不定）
    raw_time_text  TEXT,                     -- 原文时间表述（"三天后夜晚"）
    event_desc     TEXT NOT NULL,
    location       VARCHAR(200),
    participants   JSONB,
    source_chapter BIGINT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_event_timeline_book ON novel_event_timeline(book_id, chapter_id);

-- 人物状态事实账本（v2.0 设计 6.3：三时间轴之 T_narr 生效章区间，软失效不物理删除）
-- 新事实入账时将旧事实 valid_to_chapter 置为新区间前章；倒叙/回溯按"回忆目标时刻"取快照。
-- 当前态表 novel_character_state 保留为性能投影，本表为可回溯权威账本（可从本表重建投影）。
CREATE TABLE IF NOT EXISTS novel_state_fact (
    id                 BIGSERIAL PRIMARY KEY,
    book_id            BIGINT NOT NULL,
    character_id       BIGINT NOT NULL,
    source_chapter     BIGINT,                 -- 快照来源章（T_narr）
    valid_from_chapter BIGINT NOT NULL,
    valid_to_chapter   BIGINT,                 -- null = 当前有效（软失效永不物理删除）
    state              JSONB NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_state_fact_lookup ON novel_state_fact(book_id, character_id, valid_from_chapter);

-- 伏笔知情集合（v2.0 M2-C5：秘密的闭世界判定——查询"谁知道"，不穷举"谁不知道"）
-- whoKnows 非空即视为"带知情边界的事实"；secretKey 为正文可检索短关键词（RC-05 文本定位用）
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS who_knows JSONB;
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS secret_key VARCHAR(200);

-- 待证实断言区（v2.0 M2-C6：非 FACT 断言隔离——升格 >=2 独立锚点，30 章未证实过期）
CREATE TABLE IF NOT EXISTS novel_pending_assertion (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    character_id   BIGINT,
    assertion_type VARCHAR(20) NOT NULL,    -- LIE / HEARSAY / HYPOTHETICAL / DREAM
    payload        JSONB NOT NULL,          -- 抽取快照原文（升格时作为 FACT 依据）
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING / CONFIRMED / EXPIRED / REJECTED
    confirmations  INTEGER NOT NULL DEFAULT 0,
    expires_chapter BIGINT,                 -- chapterId + 30；过期清理进归档（不物理删除）
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_pending_assertion_book ON novel_pending_assertion(book_id, status);

-- 规则误报豁免（v2.0 M3-D6 误报闭环：作者标记误报 → 豁免白名单 → 回归集）
-- 豁免即该规则对本书静默；豁免清单同时是规则调整时必须重跑的回归集数据源
CREATE TABLE IF NOT EXISTS novel_rule_exemption (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    rule_id      VARCHAR(30) NOT NULL,
    reason       TEXT,
    chapter_from BIGINT,                     -- M4-E5 保护区起始章（与 chapter_to 同为空 = 整本豁免）
    chapter_to   BIGINT,                     -- M4-E5 保护区结束章（闭区间；区间内只告警不阻塞）
    review_at    TIMESTAMP WITH TIME ZONE,   -- M4-E5 到期复核（为空 = 长期有效；过期保护区失效）
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rule_exemption UNIQUE (book_id, rule_id)
);
CREATE INDEX IF NOT EXISTS idx_rule_exemption_book ON novel_rule_exemption(book_id);
CREATE INDEX IF NOT EXISTS idx_rule_exemption_review ON novel_rule_exemption(review_at);

-- 安全审计账本（v2.0 设计 7.1/7.4：三层防线全量留证，拦截率/误杀率双指标数据源）
-- 三要素：谁（book/user）、因何（layer+category+detail）、如何处置（action）
CREATE TABLE IF NOT EXISTS novel_safety_log (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT,
    layer      VARCHAR(20) NOT NULL,     -- PRE / STREAM / POST（前置/中置/后置）
    category   VARCHAR(40) NOT NULL,     -- PROMPT_INJECTION / SUSPECTED_INJECTION / CONSTRAINT_VIOLATION / ...
    action     VARCHAR(20) NOT NULL,     -- REJECT / FLAG / REWRITE / PASS
    detail     TEXT,                     -- 转义后的留证（控制符剥离 + 长度截断，防日志二次注入）
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_safety_log_book ON novel_safety_log(book_id, layer);
