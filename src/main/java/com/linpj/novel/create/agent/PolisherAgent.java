package com.linpj.novel.create.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 审校编辑 Agent（REFINED 模式第 4 阶段）
 *
 * - 职责：文风统一、冗余删减、收尾润色
 * - 硬约束：不得改动情节与设定、人物言行与事实（prompt 硬约束 + Critic 复核双保险）
 * - 剥离正文中的内联伏笔标记 <!--plot:-->，输出干净终稿（设计 5.3）
 * - 流式输出；无记忆
 *
 * @author HL
 */
@Service
public class PolisherAgent {

    /** Polisher 单次润色结果（干净终稿 + token 用量） */
    public record PolishResult(String text, long tokens) {
    }

    /** Polisher wall-clock 超时（对齐 Writer 180s） */
    private static final Duration POLISH_TIMEOUT = Duration.ofSeconds(180);

    private final ChatClient chatClient;

    public PolisherAgent(ChatModel chatModel) {
        // 无记忆 ChatClient：润色是单次转换任务
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 润色终稿
     *
     * @param draft   通过审查的正文（可能含内联伏笔标记）
     * @param style   风格要求（可空）
     * @param onChunk 增量内容回调（SSE content 事件）
     */
    public PolishResult polish(String draft, String style, Consumer<String> onChunk) {
        StringBuilder aggregated = new StringBuilder();
        AtomicLong tokens = new AtomicLong();

        chatClient.prompt()
                .system(buildSystemPrompt(style))
                .user(buildUserPrompt(draft))
                .stream()
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
                .blockLast(POLISH_TIMEOUT);

        long totalTokens = tokens.get() > 0 ? tokens.get() : estimateTokens(aggregated.toString());
        return new PolishResult(aggregated.toString(), totalTokens);
    }

    private String buildSystemPrompt(String style) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是资深审校编辑。对正文进行收尾润色：统一文风、删减冗余、提升表达质量。

                【硬约束（违反即废稿）】
                1. 不得改动情节走向、事件顺序与因果关系。
                2. 不得改动世界观设定、人物设定与人物言行的含义。
                3. 不得增删事实信息（人物获知的信息、物品、伤势、时间点等）。
                4. 不得新增或删除任何情节元素。
                5. 移除正文中所有 <!--plot: ...--> 标记（直接删除，不保留任何痕迹）。
                6. 直接输出润色后的完整正文，不要输出说明、标题或任何解释性文字。
                """);
        if (style != null && !style.isBlank()) {
            prompt.append("\n【文风基准】\n保持 ").append(style).append(" 的写作风格。\n");
        }
        return prompt.toString();
    }

    private String buildUserPrompt(String draft) {
        return "【待润色正文】\n" + draft;
    }

    // ------------------------------------------------------------------
    // 与 WriterAgent 一致的响应处理工具
    // ------------------------------------------------------------------

    static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        AssistantMessage output = response.getResult().getOutput();
        return output.getText();
    }

    static void captureUsage(ChatResponse response, AtomicLong tokens) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
            tokens.set(usage.getTotalTokens());
        }
    }

    static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length();
    }
}
