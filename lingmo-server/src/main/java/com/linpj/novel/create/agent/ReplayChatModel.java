package com.linpj.novel.create.agent;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 回放 ChatModel：以 {@link ModelReplay} 为后端替代真实模型（实验与测试注入）
 *
 * - 用途：S1/S4 探针的确定性重放、管道级回归测试、消融实验的成本为零复现
 * - 命中：返回录制文本，并把录制的 token 总量装进 usage 元数据
 *   （复用现有 WriterAgent.usageOf 读取路径，账本埋点口径一致）
 * - 未命中：快速失败并给出键哈希与 prompt 前缀——绝不编造响应（回放的证据链价值依赖于此）
 * - 流式：聚合为单块下发（回放不逐 token；Agent 侧本就聚合成完整文本，消费端无感）
 *
 * @author HL
 */
public class ReplayChatModel implements ChatModel {

    private final ModelReplay replay;

    public ReplayChatModel(ModelReplay replay) {
        this.replay = replay;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ModelReplay.RecordedCall call = replay.find(prompt);
        if (call == null) {
            throw new IllegalStateException("回放未命中（拒绝编造响应）: key=" + ModelReplay.keyOf(
                            PromptTexts.systemOf(prompt), PromptTexts.userOf(prompt))
                    + ", systemHead=" + head(PromptTexts.systemOf(prompt))
                    + ", userHead=" + head(PromptTexts.userOf(prompt)));
        }
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(call.model() == null ? "replay" : call.model())
                .usage(new DefaultUsage(null, null, (int) Math.min(Integer.MAX_VALUE, call.tokens())))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(call.response()))), metadata);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    private static String head(String text) {
        if (text == null || text.isEmpty()) {
            return "(empty)";
        }
        return text.substring(0, Math.min(60, text.length())).replace("\n", "\\n");
    }
}
