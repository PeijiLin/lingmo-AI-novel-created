package com.linpj.novel.create.agent;

import com.linpj.novel.create.config.PipelineCriticModelProperties;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import com.linpj.novel.create.pojo.pipeline.Scene;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 责编 Agent：一致性审查，输出结构化问题清单（本架构的价值核心）
 *
 * - 无记忆：每次审查独立判定，避免被上一轮结论锚定
 * - RAG：复用 bookId 过滤检索历史章节片段作为事实材料
 * - 只依据给定事实材料审查，禁止凭主观偏好；每个问题必须给出 evidence 原文引用
 * - 通过规则：overallScore >= 7 且无 HIGH 问题即 approved（阈值阶段1实测后校准）
 *
 * @author HL
 */
@Service
public class CriticAgent {

    /** Critic 单次审查结果（结构化报告 + token 用量） */
    public record ReviewResult(ReviewReport report, long tokens) {
    }

    /** 成对比较结果（ADR-6：选优用成对比较，绝对分只做门槛——跨调用绝对分漂移可达 1 分，不可用于比较） */
    public record CompareResult(String winner, String reason, long tokens) {
        public boolean aWins() {
            return "A".equalsIgnoreCase(winner);
        }

        public boolean bWins() {
            return "B".equalsIgnoreCase(winner);
        }
    }

    /** Critic wall-clock 超时（设计 4.4：Critic 60s） */
    private static final long CRITIC_TIMEOUT_MILLIS = 60_000L;

    private final ChatClient chatClient;
    private final RagAdvisorFactory ragAdvisorFactory;
    private final BeanOutputConverter<ReviewReport> converter = new BeanOutputConverter<>(ReviewReport.class);

    /**
     * 生产装配（B2-3：异源 Critic）——未配置专用模型时回退主模型（现状行为）。
     *
     * @param criticModelProperties 异源模型配置（novel.pipeline.models.critic.*）
     * @param mainApiKey            主配置 API Key（异源 key 未配置时继承）
     * @param mainBaseUrl           主配置 base-url（异源 base-url 未配置时继承）
     */
    public CriticAgent(ChatModel chatModel, RagAdvisorFactory ragAdvisorFactory, ModelCallLog modelCallLog,
                       PipelineCriticModelProperties criticModelProperties,
                       @Value("${spring.ai.openai.chat.api-key:}") String mainApiKey,
                       @Value("${spring.ai.openai.chat.base-url:}") String mainBaseUrl) {
        // 无记忆 ChatClient：审查独立判定；录制 advisor 置于最低优先级，
        // 记录 RAG 检索增强后的最终 prompt（回放确定性依赖此顺序）
        this.chatClient = ChatClient.builder(
                        resolveCriticModel(chatModel, criticModelProperties, mainApiKey, mainBaseUrl))
                .defaultAdvisors(new ModelCallRecorder("CRITIC", modelCallLog))
                .build();
        this.ragAdvisorFactory = ragAdvisorFactory;
    }

    /**
     * 异源模型解析（B2-3）：配置了专用 model 时独立构建 OpenAiChatModel（同网关换模型），
     * 否则回退主模型。包可见：供单测直接验证路由决策（不为测试暴露到 public API）。
     */
    static ChatModel resolveCriticModel(ChatModel primary,
                                        PipelineCriticModelProperties properties,
                                        String mainApiKey,
                                        String mainBaseUrl) {
        if (properties == null || properties.getModel() == null || properties.getModel().isBlank()) {
            return primary; // 未配置：现状行为（与 Writer 同源）
        }
        String apiKey = properties.getApiKey() != null && !properties.getApiKey().isBlank()
                ? properties.getApiKey() : mainApiKey;
        String baseUrl = properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                ? properties.getBaseUrl() : mainBaseUrl;
        OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(properties.getModel()).build())
                .build();
    }

    /**
     * 审查正文，返回结构化 ReviewReport
     */
    public ReviewResult review(CriticContext context) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt);
        if (context.getBookId() != null) {
            RetrievalAugmentationAdvisor ragAdvisor = ragAdvisorFactory.forBook(context.getBookId());
            spec.advisors(ragAdvisor);
        }

        ChatResponse response = Objects.requireNonNull(
                spec.call().chatResponse(), "Critic 响应为空");

        String json = WriterAgent.extractText(response);
        ReviewReport report = converter.convert(Objects.requireNonNullElse(json, ""));
        long tokens = WriterAgent.usageOf(response);
        if (tokens <= 0) {
            tokens = WriterAgent.estimateTokens(json);
        }
        if (report == null) {
            // 结构化解析彻底失败：视为通过（宁可放过不可改错，与 Critic 故障降级策略一致）
            report = new ReviewReport();
            report.setApproved(true);
            report.setOverallScore(7);
            report.setSummary("审查响应解析失败，按通过处理（降级）");
        }
        normalize(report);
        return new ReviewResult(report, tokens);
    }

    /**
     * 润色复核（设计 3.4：Polisher "情节不动硬约束 + Critic 复核"的双保险）
     *
     * 对比原稿与润色稿，判定润色是否只改文风/冗余而未动情节、设定、人物言行与事实。
     * 复核不通过时编排器回退原稿并标记 degraded。
     *
     * @param context       Critic 上下文（draft 字段放润色稿）
     * @param originalDraft 润色前的原稿
     */
    public ReviewResult verifyPolish(CriticContext context, String originalDraft) {
        String userPrompt = """
                请对比以下【原稿】与【润色稿】，判定润色是否违反"只改文风、不动情节"的约束。

                【只报告润改造成的事实性差异，以下属于允许项不得报告】
                - 文风优化、词语润饰、句式调整
                - 冗余删减（未删除情节要素）
                - 标点与格式修正

                【必须报告的差异类型】
                - PLOT_HOLE：情节/事件/因果关系被改动、增删
                - TIMELINE：时间顺序被改动
                - WORLDVIEW_CONFLICT：世界观设定被改动
                - CHARACTER_OOC：人物言行的含义被改变
                - STYLE_DRIFT：人物称谓、关键专名被擅自更改

                无事实性改动则 approved=true 且 issues 为空数组。只输出 JSON。

                【原稿】
                """ + originalDraft + "\n\n【润色稿】\n" + context.getDraft();

        ChatResponse response = Objects.requireNonNull(
                chatClient.prompt()
                        .system("你是审校复核员。原稿已定稿，润色只允许改文风与冗余。逐段比对两稿，只报告事实性改动。")
                        .user(userPrompt)
                        .call()
                        .chatResponse(),
                "复核响应为空");

        String json = WriterAgent.extractText(response);
        ReviewReport report = converter.convert(Objects.requireNonNullElse(json, ""));
        long tokens = WriterAgent.usageOf(response);
        if (tokens <= 0) {
            tokens = WriterAgent.estimateTokens(json);
        }
        if (report == null) {
            // 复核解析失败：宁可采用原稿也不冒险（视为未通过）
            report = new ReviewReport();
            report.setApproved(false);
            report.setOverallScore(5);
            report.setSummary("润色复核解析失败，按未通过处理（回退原稿）");
        }
        normalize(report);
        return new ReviewResult(report, tokens);
    }

    // ------------------------------------------------------------------
    // 成对比较（ADR-6：选优专用，不与绝对分混用）
    // ------------------------------------------------------------------

    /**
     * 成对比较两稿（同批评审一次性对比，消除跨调用绝对分漂移）。
     *
     * 用途：PRECISE 择优 / 修订回滚判定（v2.0 M3-D2，评审 N1 必改）。
     * A 稿为挑战者（新稿），B 稿为擂主（历史最优/既胜稿）；TIE 判给 B（保守：现状稿优先，避免抖动）。
     *
     * @param context 事实基准（outline/设定/摘要；draft 字段忽略，比较稿由参数显式传入）
     */
    public CompareResult compare(CriticContext context, String draftA, String draftB) {
        BeanOutputConverter<CompareVerdict> verdictConverter = new BeanOutputConverter<>(CompareVerdict.class);
        String userPrompt = "请对比以下两稿（A 稿与 B 稿），判优。\n\n"
                + verdictConverter.getFormat()
                + "\n\n【A 稿（挑战者/新稿）】\n" + nullSafe(draftA)
                + "\n\n【B 稿（擂主/既有稿）】\n" + nullSafe(draftB) + "\n\n"
                + buildFactualBase(context);

        ChatResponse response = Objects.requireNonNull(
                chatClient.prompt()
                        .system("""
                                你是严苛的小说责编，做两稿判优。只依据给定事实材料（设定/角色/摘要/伏笔/大纲），
                                只比较一致性、情节因果、人物行为可信度与信息密度；文笔好坏不作主要依据。
                                胜者仅限 A/B；两稿接近难分时判 TIE（保守：判 TIE 即保持 B 稿）。
                                winner 只能输出 A、B 或 TIE，reason 一句话给出胜因。
                                """)
                        .user(userPrompt)
                        .call()
                        .chatResponse(),
                "比较响应为空");

        String json = WriterAgent.extractText(response);
        long tokens = WriterAgent.usageOf(response);
        if (tokens <= 0) {
            tokens = WriterAgent.estimateTokens(json);
        }
        CompareVerdict verdict = verdictConverter.convert(Objects.requireNonNullElse(json, ""));
        if (verdict == null || verdict.winner() == null) {
            // 判优解析失败：保守归 TIE（保持擂主，不引入新控制流风险）
            return new CompareResult("TIE", "判优响应解析失败，保守保持既有稿", tokens);
        }
        String winner = verdict.winner().trim().toUpperCase();
        if (!"A".equals(winner) && !"B".equals(winner) && !"TIE".equals(winner)) {
            winner = "TIE";
        }
        return new CompareResult(winner, nullSafe(verdict.reason()), tokens);
    }

    /** 内部判优 JSON 契约 */
    private record CompareVerdict(String winner, String reason) {
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /** 事实基准段（判优与审查共用口径，不含待审稿本身） */
    private String buildFactualBase(CriticContext context) {
        StringBuilder sb = new StringBuilder();
        if (WriterAgent.notBlank(context.getWorldSetting())) {
            sb.append("【世界观设定（事实基准）】\n").append(context.getWorldSetting()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getCharacterInfo())) {
            sb.append("【人物设定与当前状态（事实基准）】\n").append(context.getCharacterInfo()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getStoryDigest())) {
            sb.append("【前情摘要（事实基准）】\n").append(context.getStoryDigest()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getActivePlotThreads())) {
            sb.append("【活跃伏笔账本（事实基准）】\n").append(context.getActivePlotThreads()).append("\n\n");
        }
        if (context.getOutline() != null) {
            sb.append("【本章大纲（情节基准）】\n").append(outlineOf(context)).append("\n\n");
        }
        return sb.toString();
    }

    private String outlineOf(CriticContext context) {
        // 大纲渲染复用 Writer 的口径（标题+场景列表），避免两套渲染漂移
        return renderOutlineBrief(context.getOutline());
    }

    /** 大纲简要渲染（判优基准用） */
    private String renderOutlineBrief(ChapterOutline outline) {
        StringBuilder sb = new StringBuilder();
        sb.append("章节标题：").append(nullSafe(outline.getChapterTitle())).append("\n");
        if (outline.getScenes() != null) {
            for (int i = 0; i < outline.getScenes().size(); i++) {
                Scene scene = outline.getScenes().get(i);
                sb.append("场景").append(i + 1).append("：")
                        .append(nullSafe(scene.getLocation()))
                        .append("；冲突=").append(nullSafe(scene.getConflict()))
                        .append("；结果=").append(nullSafe(scene.getOutcome())).append("\n");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Prompt 构建
    // ------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
                你是严苛的小说责编，只做一致性审查，不评价文笔好坏。

                【审查规则】
                1. 只依据给定的事实材料（世界观设定/角色卡/前情摘要/伏笔账本/大纲/检索到的历史片段）审查，禁止凭主观偏好。
                2. 每个问题必须给出 evidence（引用正文的原文片段），没有证据依据的问题不得上报。
                3. 逐项检查以下维度：
                   - CHARACTER_OOC：人物言行是否脱离角色卡设定或当前状态（含语气与说话习惯）
                   - TIMELINE：时间线是否前后矛盾
                   - PLOT_HOLE：情节漏洞（与大纲冲突、事件因果断裂）
                   - WORLDVIEW_CONFLICT：与世界观设定冲突
                   - STYLE_DRIFT：文风与既定风格明显不符（仅明显漂移才上报）
                4. severity 定义：HIGH=事实硬伤必须修改；MEDIUM=明显不一致建议修改；LOW=轻微瑕疵可忽略。
                5. overallScore 为 1-10 整体一致性评分；当且仅当 overallScore>=7 且不存在 HIGH 问题时 approved=true。
                6. 无问题时 issues 返回空数组，不要编造问题。

                【dimensionScores：六维打分（0-5 整数，各项缺省 3 分左右据实浮动）】
                - PLOT_ADVANCE 情节推进：是否实质推进主线/支线（5 分=有不可逆的状态改变）
                - CHARACTER 人物可信：动机成立、不 OOC、不降智（5 分=行为由已建立的动机必然导出）
                - CONFLICT 冲突张力：对抗强度与代价（5 分=有明确对手、代价与不确定性）
                - PACING 节奏控制：铺垫-爆发比例、信息密度（5 分=无冗余段落）
                - PROSE 语言质感：画面感、对白自然度（5 分=具体可感、无解释性对白）
                - ENDING_HOOK 章末钩子：悬念强度与合理性（5 分=必须翻页且非廉价悬念）

                【readOnScore：续读意愿 1-7 整数】
                模拟本书的目标读者，读完本章后继续翻页的意愿（1=弃书，4=中性，7=熬夜追更）。
                独立打分，不计入 overallScore。

                【输出要求】
                只输出 JSON，不要输出任何解释性文字或 Markdown 代码块标记。
                """;
    }

    private String buildUserPrompt(CriticContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请审查以下小说正文：\n\n【待审正文】\n").append(context.getDraft()).append("\n\n");

        if (WriterAgent.notBlank(context.getWorldSetting())) {
            prompt.append("【世界观设定（事实基准）】\n").append(context.getWorldSetting()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getCharacterInfo())) {
            prompt.append("【人物设定与当前状态（事实基准）】\n").append(context.getCharacterInfo()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getStoryDigest())) {
            prompt.append("【前情摘要（事实基准）】\n").append(context.getStoryDigest()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getActivePlotThreads())) {
            prompt.append("【活跃伏笔账本（事实基准）】\n").append(context.getActivePlotThreads()).append("\n\n");
        }
        if (context.getOutline() != null) {
            prompt.append("【本章大纲（情节基准）】\n章节标题：")
                    .append(WriterAgent.nullSafe(context.getOutline().getChapterTitle(), "待定")).append("\n");
            if (context.getOutline().getScenes() != null) {
                context.getOutline().getScenes().forEach(scene ->
                        prompt.append("- 场景：").append(WriterAgent.nullSafe(scene.getLocation(), "待定"))
                                .append("；冲突：").append(WriterAgent.nullSafe(scene.getConflict(), "无"))
                                .append("；预期结果：").append(WriterAgent.nullSafe(scene.getOutcome(), "待定")).append("\n"));
            }
            prompt.append("\n");
        }
        if (WriterAgent.notBlank(context.getUserInput())) {
            prompt.append("【用户创作意图】\n").append(context.getUserInput()).append("\n\n");
        }
        prompt.append(converter.getFormat());
        return prompt.toString();
    }

    /** 结构化输出的健壮性兜底：空值归一 */
    private void normalize(ReviewReport report) {
        if (report.getIssues() == null) {
            report.setIssues(new java.util.ArrayList<>());
        }
        report.getIssues().removeIf(Objects::isNull);
        if (report.getOverallScore() == null) {
            report.setOverallScore(report.isApproved() ? 7 : 5);
        }
        if (report.getOverallScore() >= 7 && !report.hasHighIssue()) {
            report.setApproved(true);
        }
        if (report.getOverallScore() < 7 || report.hasHighIssue()) {
            report.setApproved(false);
        }
    }
}
