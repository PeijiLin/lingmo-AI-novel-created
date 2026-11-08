package com.linpj.novel.create.agent;

import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 策划编辑 Agent：将用户意图转成结构化章节大纲
 *
 * - 阻塞调用，非流式；无记忆
 * - 结构化输出：ChapterOutline（.chatResponse() + BeanOutputConverter 解析）
 * - 失败降级由编排器处理（跳过 Planner，Writer 直接吃用户输入，管道不中断）
 *
 * @author HL
 */
@Service
public class PlannerAgent {

    /** Planner 单次规划结果（结构化大纲 + token 用量） */
    public record PlanResult(ChapterOutline outline, long tokens) {
    }

    private final ChatClient chatClient;
    private final BeanOutputConverter<ChapterOutline> converter = new BeanOutputConverter<>(ChapterOutline.class);

    public PlannerAgent(ChatModel chatModel) {
        // 无记忆 ChatClient
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 规划章节大纲
     */
    public PlanResult plan(PlannerContext context) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context);

        ChatResponse response = Objects.requireNonNull(
                chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .chatResponse(),
                "Planner 响应为空");

        String json = WriterAgent.extractText(response);
        ChapterOutline outline = converter.convert(Objects.requireNonNullElse(json, ""));
        long tokens = WriterAgent.usageOf(response);
        if (tokens <= 0) {
            tokens = WriterAgent.estimateTokens(json);
        }
        return new PlanResult(outline, tokens);
    }

    private String buildSystemPrompt() {
        return """
                你是资深小说策划。基于世界观与前情摘要，为下一章产出结构化大纲（JSON Schema 已给定）。

                【策划规则】
                1. 每个场景必须有冲突（conflict 不可为空），场景数量 2-5 个，按叙事顺序排列。
                2. 场景的 participantIds 与章节的 characterIds 必须从给定的角色列表中选择（引用角色 ID）。
                3. 伏笔操作（plotOps）：
                   - RESOLVE / ADVANCE 必须引用已存在伏笔的 threadId（来自活跃伏笔清单）
                   - PLANT 新埋伏笔时 threadId 留空，description 必填
                   - 没有伏笔操作时 plotOps 返回空数组
                4. 章节大纲要与前情摘要自然衔接，不重复已发生的情节。
                5. 只输出 JSON，不要输出任何解释性文字或 Markdown 代码块标记。
                """;
    }

    private String buildUserPrompt(PlannerContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为小说《").append(WriterAgent.nullSafe(context.getTitle(), "未命名")).append("》规划下一章大纲。\n\n");
        if (WriterAgent.notBlank(context.getWorldSetting())) {
            prompt.append("【世界观设定】\n").append(context.getWorldSetting()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getCharacterInfo())) {
            prompt.append("【角色列表（characterIds 从中选择）】\n").append(context.getCharacterInfo()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getStoryDigest())) {
            prompt.append("【前情摘要】\n").append(context.getStoryDigest()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getActivePlotThreads())) {
            prompt.append("【活跃伏笔清单（threadId 从中选择）】\n").append(context.getActivePlotThreads()).append("\n\n");
        }
        if (WriterAgent.notBlank(context.getStyle())) {
            prompt.append("【风格要求】\n").append(context.getStyle()).append("\n\n");
        }
        prompt.append("【用户创作意图】\n")
                .append(WriterAgent.nullSafe(context.getUserInput(), "（无特定要求，按前情自然推进）"))
                .append("\n\n");
        prompt.append(converter.getFormat());
        return prompt.toString();
    }
}
