-- ============================================================================
-- 多智能体创作流水�? DDL（novel_system 库）
-- 对应设计文档：docs/MULTI_AGENT_CREATION_DESIGN.md
-- 阶段1：creation_task / creation_stage_artifact（任务与阶段产物落库 + token 埋点�?
-- 阶段2：World State 账本四张�? + book_info.world_setting（服务端世界观唯�?事实源）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 阶段1：创作任务与阶段产物
-- ---------------------------------------------------------------------------

-- 创作任务（一次管道执行的记录，统�? token/耗时，为成本面板打底�?
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

-- 阶段产物（每阶段结构化产物实时落库，异常中断后产物仍在，支持基于 last artifact 续跑�?
CREATE TABLE IF NOT EXISTS creation_stage_artifact (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    stage      VARCHAR(30)  NOT NULL,               -- PLANNING / WRITING / CRITIC / POLISHING
    round      INTEGER      NOT NULL DEFAULT 1,     -- 重写轮次（首�?=1�?
    artifact   JSONB        NOT NULL,               -- 结构化产物：outline / draft / review / polished
    tokens     INTEGER,                             -- 本阶�? token 用量
    elapsed_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_task ON creation_stage_artifact(task_id);

-- ---------------------------------------------------------------------------
-- 阶段2：World State 账本（共享上下文，多 Agent �?致�?�靠账本而非模型记�?�）
-- ---------------------------------------------------------------------------

-- 滚动前情摘要（book 级唯�?，控制在 2000 字内�?
CREATE TABLE IF NOT EXISTS novel_story_digest (
    book_id         BIGINT PRIMARY KEY,
    summary         TEXT NOT NULL,
    last_chapter_id BIGINT,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 章节大纲（Planner 产物，含修订版本�?
CREATE TABLE IF NOT EXISTS novel_chapter_outline (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    chapter_id BIGINT,
    outline    JSONB  NOT NULL,                     -- ChapterOutline 序列�?
    version    INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outline_book ON novel_chapter_outline(book_id);

-- 伏笔账本（核心资产：OPEN / RESOLVED / ABANDONED�?
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

-- 人物动�?�状态（角色卡是静�?�设定，这张是随剧情推进的当前�?�；book+character 唯一�?
CREATE TABLE IF NOT EXISTS novel_character_state (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    chapter_id   BIGINT,                             -- 状�?�快照至哪章
    state        JSONB  NOT NULL,                    -- {位置, 心�??, 新获知信�?, 持有物品, 受伤/能力变化}
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_char_state UNIQUE (book_id, character_id)
);

-- 世界观唯�?事实源：book_info 增加 world_setting 列（前端仍可传，仅作覆盖提示�?
ALTER TABLE book_info ADD COLUMN IF NOT EXISTS world_setting TEXT;

-- ---------------------------------------------------------------------------
-- 阶段4：Kafka 异步任务依赖（幂等栅栏由 creation_task.status 承担，无�?额外表）
-- topic 由应用启动时自动创建（KafkaConfig）：
--   novel.creation.task      整章/批量生成异步任务
--   novel.chapter.finalized  章节内容定稿（摘要滚�? + 人物状�?�抽取）
-- ---------------------------------------------------------------------------

-- ��ͻ�ٲ��˱���v2.0 ��� 6.4����ͻ����Ĭ���ǣ�˫�����ۣ�
-- R1 ��Ȼ�ݻ����� ALIVE->DEAD�����Զ���Ч + AUTO_APPLIED ����
-- R3 �������ˣ�DEAD->ALIVE�����Ƹ���/д�����󣩣���״̬���������ˣ�PENDING_REVIEW �������ٲ�
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

-- �¼�ʱ���ߣ�v2.0 ��� 6.3 ��ʱ����֮ T_story������ʱ�䰴"����������"��һ����
-- LLM ֻ����ṹ��ƫ�ƣ���ֵ+��λ+���գ����������������㣨���� P3 �ֹ����þ��߷������� LLM �㣩
-- day_index�����ڵ� N �죨�����ᣩ��null = ʱ�䲻����UNKNOWN/ABSOLUTE_LABEL����ʱ��У�齵��Ϊƫ��
CREATE TABLE IF NOT EXISTS novel_event_timeline (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    scene_no       INTEGER,
    narrative_mode VARCHAR(20) NOT NULL DEFAULT 'NORMAL',   -- NORMAL/FLASHBACK/DREAM/STORY_IN_STORY/UNRELIABLE��C4��
    time_kind      VARCHAR(30) NOT NULL,     -- EXACT / RELATIVE_RESOLVED / FUZZY / UNKNOWN / ABSOLUTE_LABEL
    day_index      INTEGER,                  -- T_story ��һ��������null=������
    raw_time_text  TEXT,                     -- ԭ��ʱ�������"�����ҹ��"��
    event_desc     TEXT NOT NULL,
    location       VARCHAR(200),
    participants   JSONB,
    source_chapter BIGINT,
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_event_timeline_book ON novel_event_timeline(book_id, chapter_id);

-- ����״̬��ʵ�˱���v2.0 ��� 6.3����ʱ����֮ T_narr ��Ч�����䣬��ʧЧ������ɾ����
-- ����ʵ����ʱ������ʵ valid_to_chapter ��Ϊ������ǰ�£�����/���ݰ�"����Ŀ��ʱ��"ȡ���ա�
-- ��ǰ̬�� novel_character_state ����Ϊ����ͶӰ������Ϊ�ɻ���Ȩ���˱����ɴӱ����ؽ�ͶӰ����
CREATE TABLE IF NOT EXISTS novel_state_fact (
    id                 BIGSERIAL PRIMARY KEY,
    book_id            BIGINT NOT NULL,
    character_id       BIGINT NOT NULL,
    source_chapter     BIGINT,                 -- ������Դ�£�T_narr��
    valid_from_chapter BIGINT NOT NULL,
    valid_to_chapter   BIGINT,                 -- null = ��ǰ��Ч����ʧЧ��������ɾ����
    state              JSONB NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_state_fact_lookup ON novel_state_fact(book_id, character_id, valid_from_chapter);

-- ����֪�鼯�ϣ�v2.0 M2-C5�����ܵı������ж�������ѯ"˭֪��"�������"˭��֪��"��
-- whoKnows �ǿռ���Ϊ"��֪��߽����ʵ"��secretKey Ϊ���Ŀɼ����̹ؼ��ʣ�RC-05 �ı���λ�ã�
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS who_knows JSONB;
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS secret_key VARCHAR(200);

-- ��֤ʵ��������v2.0 M2-C6���� FACT ���Ը��롪������ >=2 ����ê�㣬30 ��δ֤ʵ���ڣ�
CREATE TABLE IF NOT EXISTS novel_pending_assertion (
    id             BIGSERIAL PRIMARY KEY,
    book_id        BIGINT NOT NULL,
    chapter_id     BIGINT,
    character_id   BIGINT,
    assertion_type VARCHAR(20) NOT NULL,    -- LIE / HEARSAY / HYPOTHETICAL / DREAM
    payload        JSONB NOT NULL,          -- ��ȡ����ԭ�ģ�����ʱ��Ϊ FACT ���ݣ�
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING / CONFIRMED / EXPIRED / REJECTED
    confirmations  INTEGER NOT NULL DEFAULT 0,
    expires_chapter BIGINT,                 -- chapterId + 30�������������鵵��������ɾ����
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_pending_assertion_book ON novel_pending_assertion(book_id, status);

-- �����󱨻��⣨v2.0 M3-D6 �󱨱ջ������߱���� �� ��������� �� �ع鼯��
-- ���⼴�ù���Ա��龲Ĭ�������嵥ͬʱ�ǹ������ʱ�������ܵĻع鼯����Դ
CREATE TABLE IF NOT EXISTS novel_rule_exemption (
    id           BIGSERIAL PRIMARY KEY,
    book_id      BIGINT NOT NULL,
    rule_id      VARCHAR(30) NOT NULL,
    reason       TEXT,
    chapter_from BIGINT,                     -- M4-E5 ��������ʼ�£��� chapter_to ͬΪ�� = �������⣩
    chapter_to   BIGINT,                     -- M4-E5 �����������£������䣻������ֻ�澯��������
    review_at    TIMESTAMP WITH TIME ZONE,   -- M4-E5 ���ڸ��ˣ�Ϊ�� = ������Ч�����ڱ�����ʧЧ��
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_rule_exemption UNIQUE (book_id, rule_id)
);
CREATE INDEX IF NOT EXISTS idx_rule_exemption_book ON novel_rule_exemption(book_id);
CREATE INDEX IF NOT EXISTS idx_rule_exemption_review ON novel_rule_exemption(review_at);

-- ��ȫ����˱���v2.0 ��� 7.1/7.4���������ȫ����֤��������/��ɱ��˫ָ������Դ��
-- ��Ҫ�أ�˭��book/user������Σ�layer+category+detail������δ��ã�action��
CREATE TABLE IF NOT EXISTS novel_safety_log (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT,
    layer      VARCHAR(20) NOT NULL,     -- PRE / STREAM / POST��ǰ��/����/���ã�
    category   VARCHAR(40) NOT NULL,     -- PROMPT_INJECTION / SUSPECTED_INJECTION / CONSTRAINT_VIOLATION / ...
    action     VARCHAR(20) NOT NULL,     -- REJECT / FLAG / REWRITE / PASS
    detail     TEXT,                     -- ת������֤�����Ʒ����� + ���Ƚضϣ�����־����ע�룩
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_safety_log_book ON novel_safety_log(book_id, layer);

-- ---------------------------------------------------------------------------
-- B1 批次（2026-11-22：PG 图语义等价实现——ADR-8 不引入图数据库，关系/因果/排期全落单栈）
-- ---------------------------------------------------------------------------

-- 人物关系生效区间（B1-1：关系边随剧情演化——旧行软失效不物理删除，语义对齐 novel_state_fact）
-- 区间为 chapter_num 域（作者视角章号）；chapter_from/chapter_to 均空 = 长期有效（存量数据兼容）
-- 消费端：RC-08 称谓/关系校验按当前章取生效边（covers 语义与 state_fact 一致）
ALTER TABLE character_relationships ADD COLUMN IF NOT EXISTS chapter_from INTEGER;
ALTER TABLE character_relationships ADD COLUMN IF NOT EXISTS chapter_to   INTEGER;
ALTER TABLE character_relationships ADD COLUMN IF NOT EXISTS intensity    SMALLINT;

-- 事件因果链（B1-2：caused_by 指向直接起因事件 ID，RC-07 因果时序校验的图数据）
-- 写入端仅接受同书已入账事件（防幻觉 ID），故必有 cause_id < effect_id，因果图天然无环
ALTER TABLE novel_event_timeline ADD COLUMN IF NOT EXISTS caused_by BIGINT;
CREATE INDEX IF NOT EXISTS idx_event_causal ON novel_event_timeline(book_id, caused_by);

-- 伏笔回收排期（B1-3：距埋设章的相对章距，避免与 book_chapter 章号域耦合）
-- planned_offset/deadline_offset 超期触发 RC-09 逾期告警并回灌 Planner（meta 信息，Writer 侧不下发）
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS planned_offset  INTEGER;
ALTER TABLE novel_plot_threads ADD COLUMN IF NOT EXISTS deadline_offset INTEGER;

-- ---------------------------------------------------------------------------
-- B3 批次（2026-11-22：F2 NodeCheckpoint——节点级断点续跑账本，M5-F2）
-- ---------------------------------------------------------------------------

-- 节点 checkpoint（编排器节点成功事实账本：断点续跑"不重放已完成节点副作用"的依据）
-- - run_id = creation_task.id（任务级 run；features/入参快照在 creation_task.params）
-- - node_id = 阶段:轮次（如 PLANNING / WRITING:1 / CRITIC:1；PRECISE 择优节点含 variant 维度时扩展命名）
-- - input_hash = 节点核心输入的内容哈希（同输入的续跑才可复用产物；输入变更视为新节点意图）
-- - 产物本体不重复存储：由 (run_id, stage, round) 定位 creation_stage_artifact 复用
-- 只写 SUCCEEDED：失败/中断节点无 checkpoint，续跑时自然重做（重试语义而非跳过）
CREATE TABLE IF NOT EXISTS novel_node_checkpoint (
    id             BIGSERIAL PRIMARY KEY,
    run_id         BIGINT NOT NULL,
    node_id        VARCHAR(60) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SUCCEEDED',
    input_hash     VARCHAR(64),
    tokens         INTEGER,
    note           VARCHAR(200),
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_node_checkpoint UNIQUE (run_id, node_id)
);
CREATE INDEX IF NOT EXISTS idx_node_checkpoint_run ON novel_node_checkpoint(run_id);

-- ---------------------------------------------------------------------------
-- B4 批次（2026-11-22：summaryTree 定长摘要树——H2 记忆分层假设的数据机制本体）
-- ---------------------------------------------------------------------------

-- 三层定长摘要树：CHAPTER（章节详摘 <=500 字）/ SEGMENT（区段中摘，每 10 章一个节点 <=600 字）/ BOOK（全书短摘 <=300 字）
-- - 上层由下层重算（代码截断强制定长不变式：摘要总量恒定，不随章数线性膨胀）
-- - seq：CHAPTER=chapter_num，SEGMENT=区段序号(0 起)，BOOK=0
-- - is_stale：写入下层时置上层 stale，重算时清除（血缘标记；渲染始终取最新内容保证近因性）
-- - 幂等重建：维护路径本身即"从子摘要全量重算上级"，写路径重入结果一致（H2 消融复现依赖）
CREATE TABLE IF NOT EXISTS novel_summary_tree (
    id         BIGSERIAL PRIMARY KEY,
    book_id    BIGINT NOT NULL,
    level      VARCHAR(10) NOT NULL,
    seq        INTEGER NOT NULL,
    chapter_id BIGINT,
    content    TEXT,
    is_stale   BOOLEAN NOT NULL DEFAULT FALSE,
    version    INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_summary_tree UNIQUE (book_id, level, seq)
);
CREATE INDEX IF NOT EXISTS idx_summary_tree_level ON novel_summary_tree(book_id, level);

-- ---------------------------------------------------------------------------
-- B5 批次（2026-11-22：F4 成本账本——token/成本核算与导出，论文 §10.7/§11.1 成本论证数据源）
-- ---------------------------------------------------------------------------

-- 节点级成本账本行（每个 run 每个节点一行，轮次折叠求和；UNIQUE(run_id, node) 幂等重算）
-- - 溯源：行由 creation_stage_artifact 派生（stage -> Agent -> model 映射确定），任务终态时单点入账
-- - mode：任务模式（FAST/STANDARD/REFINED/PRECISE），模式档位相对成本（论文 11.1）的分组维度
-- - input_tokens/output_tokens：网关 usage 元数据回传时细化（当前记 total，拆分列预留）
-- - cost：total_tokens/1000 x 价目(novel.pipeline.pricing.per-1k.{model})，未配价目 = 0（导出标注）
-- - 失败任务同样入账（已烧掉的 token 不因任务失败而消失）
CREATE TABLE IF NOT EXISTS novel_cost_ledger (
    id            BIGSERIAL PRIMARY KEY,
    run_id        BIGINT NOT NULL,
    book_id       BIGINT NOT NULL,
    chapter_id    BIGINT,
    mode          VARCHAR(20),
    node          VARCHAR(20) NOT NULL,
    model         VARCHAR(100),
    total_tokens  BIGINT NOT NULL DEFAULT 0,
    input_tokens  BIGINT,
    output_tokens BIGINT,
    cost          NUMERIC(12,6) NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cost_ledger UNIQUE (run_id, node)
);
CREATE INDEX IF NOT EXISTS idx_cost_ledger_book ON novel_cost_ledger(book_id);

-- ---------------------------------------------------------------------------
-- R0 批次（2026-11-23）：自主性改造——有界 Agent 运行时的步骤级 Checkpoint 与工具回执
-- 方案：docs/AGENTIC_RUNTIME_PLAN.md §5/§11（含实施前修正案）；基线 tag v2.0-baseline
-- ---------------------------------------------------------------------------

-- AgentRuntime 步骤账本（每步模型决策落盘 = 步骤级 Checkpoint；
-- 恢复时已完成步骤从回执重放，不重新调模型/工具——不重复计费）
-- - node_id = 阶段:轮次（与 novel_node_checkpoint 同命名空间）；step_no 从 1 起，1 步 = 1 次模型调用
-- - kind = 步骤类型：TOOL_CALL / ASK_HUMAN / FINISH（决策三出口，R1a 工具化）
-- - decision = 模型结构化决策原文（kill-resume 重放消息序列的数据源；动态部分纯函数推导）
-- - status 状态机位：RUNNING（落盘即占位）→ DONE / FAILED / SKIPPED；崩溃残留 RUNNING，续跑重做该步
-- - receipt_id → novel_tool_receipt.id（tool_call 步的真实回执；失败/被拒工具不推进状态）
-- - tokens = 本步模型调用 token：B5 节点总账之下的步级明细账（E5 消融取数层，总账-明细两层共存）
CREATE TABLE IF NOT EXISTS novel_agent_step (
    id          BIGSERIAL PRIMARY KEY,
    run_id      BIGINT NOT NULL,
    node_id     VARCHAR(60) NOT NULL,
    step_no     INTEGER NOT NULL,
    kind        VARCHAR(20) NOT NULL,
    decision    JSONB,
    receipt_id  BIGINT,
    status      VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    tokens      INTEGER NOT NULL DEFAULT 0,
    note        VARCHAR(200),
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_step UNIQUE (run_id, node_id, step_no)
);
CREATE INDEX IF NOT EXISTS idx_agent_step_run ON novel_agent_step(run_id);

-- 工具回执账本（真实回执驱动：模型不消费"想象的结果"，只消费落盘回执）
-- - 内容+次数寻址幂等：UNIQUE(run,node,tool,args_hash,attempt)——同 attempt 重放返回既有回执
--   （kill-resume 不重复执行/计费）；attempt 入键而非省略，是让"崩溃后重试预算"可从表推导：
--   最新 attempt 的 ok=false 才允许 attempt+1（工具契约"重试<=1"），纯内容寻址会把重试卡死在失败回执上
-- - 仅 run 内幂等，跨 run 不缓存（正典漂移防线：跨 run 的事实可能已变，重查便宜）
-- - ok=false 失败回照样落盘（失败不推进状态 ≠ 失败不留痕）
-- - 证据 ID 派生规则（修正案 M1）：evidence 类工具结果每条 = ev:<receipt_id>#<序号>（代码生成，
--   finish 引文只认 ID，引文校验退化为确定性存在性校验——引文有效率上限从"模型能力"变"工具可回显性"）
-- - result 一律视为不可信数据区：工具输出只作事实材料，消费侧不得当指令（注入防线）
CREATE TABLE IF NOT EXISTS novel_tool_receipt (
    id          BIGSERIAL PRIMARY KEY,
    run_id      BIGINT NOT NULL,
    node_id     VARCHAR(60) NOT NULL,
    tool        VARCHAR(60) NOT NULL,
    args_hash   VARCHAR(64) NOT NULL,
    attempt     INTEGER NOT NULL DEFAULT 0,
    result      JSONB,
    ok          BOOLEAN NOT NULL DEFAULT FALSE,
    tokens      INTEGER NOT NULL DEFAULT 0,
    cost_ms     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tool_receipt UNIQUE (run_id, node_id, tool, args_hash, attempt)
);
CREATE INDEX IF NOT EXISTS idx_tool_receipt_run ON novel_tool_receipt(run_id);

-- ---------------------------------------------------------------------------
-- R1b 批次（2026-11-24）：引文协议——citation_link 引文绑定账本
-- 方案 §11.1-M1：evidenceId 由工具回执生成（ev:<receipt_id>#<序号>），finish 引文只认 ID；
-- 引文校验退化为确定性存在性校验（receipt 存在 + 序号界内 + 引文文本与回执条目一致）
-- ---------------------------------------------------------------------------
-- 引文绑定账本（Critic L2 收口硬校验逐条落账；校验通过与否都留痕——一物四用中的审计/回放面）
-- - evidence_id = ev:<receipt_id>#<序号>（代码生成，模型 finish 时引用）
-- - receipt_id + item_index 回指 novel_tool_receipt.result 第 index 条（可回验原文）
-- - ok = 该条绑定是否通过硬校验（存在性+界内+文本一致）；violations 记具体失败原因
-- - issue 每条一绑定（issue.evidenceId → 工具回执条目）；claims[] 完整协议 R2 随 proposal.emit 扩展
CREATE TABLE IF NOT EXISTS novel_citation_link (
    id           BIGSERIAL PRIMARY KEY,
    run_id       BIGINT NOT NULL,
    node_id      VARCHAR(60) NOT NULL,
    step_no      INTEGER NOT NULL,
    claim_text   TEXT,
    evidence_id  VARCHAR(40) NOT NULL,
    receipt_id   BIGINT NOT NULL,
    item_index   INTEGER NOT NULL,
    ok           BOOLEAN NOT NULL DEFAULT FALSE,
    violations   VARCHAR(200),
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_citation_link UNIQUE (run_id, node_id, step_no, evidence_id)
);
CREATE INDEX IF NOT EXISTS idx_citation_link_run ON novel_citation_link(run_id);

-- ---------------------------------------------------------------------------
-- R2a 批次（2026-11-24）：正典提案暂存账本——Writer L2 proposal.emit 协议
-- 方案 §2：proposal.emit 只暂存不写入；提交门在节点 SUCCESS 后统一提交（消费端 R3 接）
-- ---------------------------------------------------------------------------
-- 提案形态（proposal JSONB）：{type: STATE, characterId, state, note}——对应 novel_state_fact 结构
-- - 幂等：UNIQUE(run, node, proposal_hash) 内容寻址（同提案重复 emit 跳过——工具重放走回执层）
-- - status 状态机：PENDING（暂存）→ ADOPTED / REJECTED（R3 提交门裁定，审计不可变只补记）
-- - receipt_id 关联工具回执（proposal.emit 调用的审计链）
CREATE TABLE IF NOT EXISTS novel_canon_proposal (
    id            BIGSERIAL PRIMARY KEY,
    run_id        BIGINT NOT NULL,
    node_id       VARCHAR(60) NOT NULL,
    step_no       INTEGER,
    receipt_id    BIGINT,
    proposal      JSONB NOT NULL,
    proposal_hash VARCHAR(64) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_canon_proposal UNIQUE (run_id, node_id, proposal_hash)
);
CREATE INDEX IF NOT EXISTS idx_canon_proposal_run ON novel_canon_proposal(run_id);
