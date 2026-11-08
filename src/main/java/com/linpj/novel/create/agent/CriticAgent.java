package com.linpj.novel.create.agent;

import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
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

    /** Critic wall-clock 超时（设计 4.4：Critic 60s） */
    private static final long CRITIC_TIMEOUT_MILLIS = 60_000L;

    private final ChatClient chatClient;
    private final RagAdvisorFactory ragAdvisorFactory;
    private final BeanOutputConverter<ReviewReport> converter = new BeanOutputConverter<>(ReviewReport.class);

    public CriticAgent(ChatModel chatModel, RagAdvisorFactory ragAdvisorFactory) {
        // 无记忆 ChatClient：审查独立判定
        this.chatClient = ChatClient.builder(chatModel).build();
        this.ragAdvisorFactory = ragAdvisorFactory;
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
