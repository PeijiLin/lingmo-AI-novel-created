package com.linpj.novel.create.agent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Prompt 文本抽取工具：录制与回放共用同一逻辑，保证 (system, user) 键在两端一致
 * ——回放确定性的前提（v2.0 设计 6.1）
 *
 * @author HL
 */
final class PromptTexts {

    /** 单字段截断上限：录制截断口径与回放键口径必须一致 */
    static final int MAX_FIELD_LENGTH = 64_000;

    private PromptTexts() {
    }

    /** 系统位文本（多段以空行连接；无则 null） */
    @Nullable
    static String systemOf(Prompt prompt) {
        return textOf(prompt, MessageType.SYSTEM);
    }

    /** 用户位文本（含记忆 advisor 注入的历史消息；无则 null） */
    @Nullable
    static String userOf(Prompt prompt) {
        return textOf(prompt, MessageType.USER);
    }

    @Nullable
    private static String textOf(Prompt prompt, MessageType type) {
        if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
            return null;
        }
        List<Message> instructions = prompt.getInstructions();
        String joined = instructions.stream()
                .filter(m -> m != null && m.getMessageType() == type && m.getText() != null)
                .map(Message::getText)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse(null);
        if (joined != null && joined.length() > MAX_FIELD_LENGTH) {
            return joined.substring(0, MAX_FIELD_LENGTH) + "...(truncated)";
        }
        return joined;
    }
}
