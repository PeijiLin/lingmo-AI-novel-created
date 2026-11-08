package com.linpj.novel.create.agent;

import com.linpj.novel.create.pojo.pipeline.Scene;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * 主笔 Agent：按（可选）大纲流式写正文
 *
 * - 记忆隔离：conversationId 使用 "writer:{chapterId}"，与现有 FAST 链路（裸 chapterId）隔离
 * - 重写为定向修订：带原稿 + Critic 问题清单逐场景比对修改，非从零生成
 * - 大纲场景拆进 user prompt 而非 system（降低"流式生成不听话"风险）
 * - 伏笔内联标记：正文若出现大纲外新伏笔，以 <!--plot: 伏笔描述--> 输出（编排器剥离入账）
 *
 * @author HL
 */
@Service
public class WriterAgent {

    /** Writer 单次生成结果（聚合全文 + token 用量） */
    public record WriteResult(String text, long tokens) {
    }

    /** Writer wall-clock 超时（设计 4.4：Writer 180s） */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(180);

    private final ChatClient chatClient;
    private final RagAdvisorFactory ragAdvisorFactory;

    public WriterAgent(ChatModel chatModel, MessageWindowChatMemory chatMemory, RagAdvisorFactory ragAdvisorFactory) {
        // Agent 自建 ChatClient：仅 Writer 挂记忆（复用统一 ChatMemory 仓库，conversationId 前缀隔离）
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.ragAdvisorFactory = ragAdvisorFactory;
    }

    /**
     * 首轮写作
     *
     * @param context 上下文（大纲可空 = Planner 降级直写）
     * @param onChunk 增量内容回调（SSE content 事件）
     */
    public WriteResult write(WriterContext context, Consumer<String> onChunk) {
        return writeVariant(context, 1, onChunk);
    }

    /**
     * 变体写作（PRECISE 档并行双稿用）：variant=1 标准稿；variant>1 拉开差异的变体稿
     * （大纲微变 + 提升温度种子，缓解双稿趋同风险，设计 10）
     */
    public WriteResult writeVariant(WriterContext context, int variant, Consumer<String> onChunk) {
        String systemPrompt = buildSystemPrompt(context);
        String userPrompt = buildUserPrompt(context);
        if (variant > 1) {
            userPrompt = userPrompt + "\n\n【变体要求】在严格保持大纲冲突与场景顺序不变的前提下，"
                    + "用更具张力、节奏更锐利的叙事处理完成本章，敢于留白与场景跳跃，避免平铺直叙。";
        }
        return doWrite(context, systemPrompt, userPrompt, onChunk, variant);
    }

    /**
     * 定向重写：附原稿与问题清单，只针对 HIGH/MEDIUM 问题修订，保持未受影响段落与文风
     */
    public WriteResult rewrite(WriterContext context, String draft, ReviewReport review, int round, Consumer<String> onChunk) {
        String systemPrompt = buildSystemPrompt(context);
        String userPrompt = buildRewritePrompt(context, draft, review, round);
        return doWrite(context, systemPrompt, userPrompt, onChunk, 1);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private WriteResult doWrite(WriterContext context, String systemPrompt, String userPrompt,
                                 Consumer<String> onChunk, int variant) {
        StringBuilder aggregated = new StringBuilder();
        AtomicLong tokens = new AtomicLong();

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(a -> a.param(CONVERSATION_ID, conversationId(context)));
        // 变体稿提升温度，拉开双稿差异（避开趋同导致择优失去意义）
        if (variant > 1) {
            spec.options(OpenAiChatOptions.builder().temperature(0.9).build());
        }
        // RAG：检索本书历史片段作为写作参考（bookId 为空时跳过，与现有 FAST 链路一致）
        if (context.getBookId() != null) {
            RetrievalAugmentationAdvisor ragAdvisor = ragAdvisorFactory.forBook(context.getBookId());
            spec.advisors(ragAdvisor);
        }

        spec.stream()
                .chatResponse()
                .doOnNext(response -> {
                    String chunk = extractText(response);
                    if (chunk != null && !chunk.isEmpty()) {
                        aggregated.append(chunk);
                        if (onChunk != null) {
                            onChunk.accept(chunk);
                        }
                    }
                    captureUsage(response, tokens);
                })
                .blockLast(WRITE_TIMEOUT);

        long totalTokens = tokens.get() > 0 ? tokens.get() : estimateTokens(aggregated.toString());
        return new WriteResult(aggregated.toString(), totalTokens);
    }

    private String conversationId(WriterContext context) {
        // 记忆隔离：writer:{chapterId}；无章节场景用书级兜底
        if (context.getChapterId() != null) {
            return "writer:" + context.getChapterId();
        }
        return "writer:book-" + context.getBookId();
    }

    private String buildSystemPrompt(WriterContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的小说主笔，正在协助创作小说《").append(nullSafe(context.getTitle(), "未命名")).append("》。\n\n");

        if (notBlank(context.getWorldSetting())) {
            prompt.append("【世界观设定】\n").append(context.getWorldSetting()).append("\n\n");
        }
        if (notBlank(context.getCharacterInfo())) {
            prompt.append("【人物设定】\n").append(context.getCharacterInfo()).append("\n");
        }
        if (notBlank(context.getStoryDigest())) {
            prompt.append("【前情摘要】\n").append(context.getStoryDigest()).append("\n\n");
        }
        if (notBlank(context.getActivePlotThreads())) {
            prompt.append("【活跃伏笔（可自然推进或呼应，不得矛盾）】\n").append(context.getActivePlotThreads()).append("\n\n");
        }
        if (notBlank(context.getStyle())) {
            prompt.append("【风格要求】\n请保持 ").append(context.getStyle()).append(" 的写作风格。\n\n");
        }

        prompt.append("【写作硬约束】\n");
        prompt.append("1. 人物言行必须严格符合其角色卡与当前状态。\n");
        prompt.append("2. 不得使用角色当前尚不知道的信息（防剧透泄漏）。\n");
        prompt.append("3. 若提供了本章大纲，严格按场景顺序写作。\n");
        prompt.append("4. 若正文出现大纲之外的新伏笔，在该处立即以 <!--plot: 伏笔简述--> 格式内联标记，便于编辑记账。\n\n");

        appendCreationTask(prompt, context.getCreationType());
        return prompt.toString();
    }

    private String buildUserPrompt(WriterContext context) {
        StringBuilder prompt = new StringBuilder();
        if (context.getOutline() != null) {
            prompt.append("【本章大纲】\n").append(renderOutline(context.getOutline())).append("\n\n");
            prompt.append("请按上述大纲逐场景完成本章正文：");
        }
        prompt.append("【用户输入】\n").append(nullSafe(context.getUserInput(), "（无，按大纲自由创作）"));
        return prompt.toString();
    }

    private String buildRewritePrompt(WriterContext context, String draft, ReviewReport review, int round) {
        StringBuilder prompt = new StringBuilder();
        if (context.getOutline() != null) {
            prompt.append("【本章大纲（修改后仍需符合）】\n").append(renderOutline(context.getOutline())).append("\n\n");
        }
        prompt.append("【原稿】\n").append(draft).append("\n\n");
        prompt.append("【责编审查问题清单（第 ").append(round).append(" 轮）】\n");
        if (review != null && review.getIssues() != null) {
            review.getIssues().forEach(issue -> prompt.append("- [").append(nullSafe(issue.getSeverity(), "MEDIUM"))
                    .append("][").append(nullSafe(issue.getType(), "GENERAL")).append("] ")
                    .append(nullSafe(issue.getProblem(), ""))
                    .append("\n  修改建议：").append(nullSafe(issue.getSuggestion(), "无")).append("\n"));
        }
        prompt.append("\n请基于原稿做定向修订重写：只针对上述问题逐项修改，未受影响的段落保持原文与文风，不要从零重写，不要改变既定情节走向。直接输出修订后的完整正文。");
        return prompt.toString();
    }

    private void appendCreationTask(StringBuilder prompt, String creationType) {
        prompt.append("【任务】\n");
        String type = creationType == null ? "" : creationType;
        switch (type) {
            case "续写" -> prompt.append("请基于上述信息和之前的对话历史，自然地续写小说内容。确保风格、人物性格和世界观保持一致。");
            case "对话" -> prompt.append("请根据角色设定，生成符合其性格和背景的对话。对话应自然流畅，推进情节发展。");
            case "情节" -> prompt.append("请根据世界观和人物关系，构思一个合理的情节发展或冲突点。");
            case "描写" -> prompt.append("请对场景、人物外貌或动作进行细致描写。");
            case "润色" -> prompt.append("请对提供的文本内容进行润色优化，提升文字表达质量，使语言更生动、流畅，同时保持原意不变。");
            case "灵感" -> prompt.append("请根据用户提供的关键词，结合小说的世界观和人物设定，激发创作灵感，构思新颖的情节或故事发展方向。");
            default -> prompt.append("请根据用户输入，结合小说设定进行创作。确保内容符合小说的整体风格和逻辑。");
        }
    }

    private String renderOutline(ChapterOutline outline) {
        StringBuilder sb = new StringBuilder();
        sb.append("章节标题：").append(nullSafe(outline.getChapterTitle(), "待定")).append("\n");
        List<Scene> scenes = outline.getScenes();
        if (scenes != null) {
            for (int i = 0; i < scenes.size(); i++) {
                Scene scene = scenes.get(i);
                sb.append("场景").append(i + 1).append("：地点=").append(nullSafe(scene.getLocation(), "待定"))
                        .append("；冲突=").append(nullSafe(scene.getConflict(), "无"))
                        .append("；结果=").append(nullSafe(scene.getOutcome(), "待写")).append("\n");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 通用工具
    // ------------------------------------------------------------------

    static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        AssistantMessage output = response.getResult().getOutput();
        return output.getText();
    }

    /** 从响应元数据提取 token 用量（流式网关可能不回传，返回 false 交由调用方估算） */
    static void captureUsage(ChatResponse response, AtomicLong tokens) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
            tokens.set(usage.getTotalTokens());
        }
    }

    /** 从阻塞响应提取 token 用量，回退为 0 */
    static long usageOf(ChatResponse response) {
        if (response != null && response.getMetadata() != null
                && response.getMetadata().getUsage() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage.getTotalTokens() != null) {
                return usage.getTotalTokens();
            }
        }
        return 0;
    }

    /**
     * token 兜底估算：中文约 1 字 ≈ 1 token，英文约 4 字符 ≈ 1 token，取混合折中。
     * 仅用于网关不回传 usage 时的成本感知埋点，不追求精确。
     */
    static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length();
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String nullSafe(String s, String defaultValue) {
        return s == null || s.isBlank() ? defaultValue : s;
    }
}
