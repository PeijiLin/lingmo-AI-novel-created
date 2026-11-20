package com.linpj.novel.create.agent;

import com.linpj.novel.create.config.PipelineRecordingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回放链路单元测试：录制 -> 加载 -> 回放闭环（确定性核心 = 键抽取同源）
 *
 * @author HL
 */
class ModelReplayTest {

    private Path dir;

    @AfterEach
    void cleanUp() {
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir.getParent())
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // .temp 区容忍残留
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private ModelCallLog recordingCallLog() {
        dir = Paths.get(".temp", "test-replay", UUID.randomUUID().toString());
        PipelineRecordingProperties properties = new PipelineRecordingProperties();
        properties.setEnabled(true);
        properties.setDir(dir.toString());
        return new ModelCallLog(properties);
    }

    private Prompt systemUserPrompt(String system, String user) {
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        return new Prompt(messages);
    }

    private ChatClientRequest requestOf(Prompt prompt) {
        return ChatClientRequest.builder()
                .prompt(prompt)
                .context(new HashMap<>())
                .build();
    }

    private ChatClientResponse responseOf(String text, long tokens) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder()
                        .model("deepseek-v4.1-flash")
                        .usage(new DefaultUsage(null, null, (int) tokens))
                        .build());
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(new HashMap<>())
                .build();
    }

    @Test
    void 录制加载回放闭环() {
        ModelCallLog callLog = recordingCallLog();
        // 成功调用 x2（不同 prompt），失败调用 x1
        callLog.record("PLANNER", requestOf(systemUserPrompt("策划系统位", "策划用户位")),
                responseOf("大纲JSON", 1200L), 800L, null);
        callLog.record("CRITIC", requestOf(systemUserPrompt("审查系统位", "审查用户位")),
                responseOf("审查报告JSON", 600L), 400L, null);
        callLog.record("PLANNER", requestOf(systemUserPrompt("策划系统位", "策划用户位")),
                null, 100L, new IllegalStateException("网关超时"));

        ModelReplay replay = ModelReplay.load(dir.resolve("recordings.jsonl"));
        assertEquals(2, replay.size(), "失败调用不参与回放");

        // 同源键抽取：同一 Prompt 命中录制
        ModelReplay.RecordedCall hit = replay.find(systemUserPrompt("策划系统位", "策划用户位"));
        assertNotNull(hit);
        assertEquals("PLANNER", hit.agent());
        assertEquals("大纲JSON", hit.response());
        assertEquals(1200L, hit.tokens());

        assertTrue(replay.has("审查系统位", "审查用户位"));
    }

    @Test
    void 回放ChatModel_命中返回录制文本与token() {
        ModelCallLog callLog = recordingCallLog();
        callLog.record("CRITIC", requestOf(systemUserPrompt("审系统", "审用户")),
                responseOf("通过", 555L), 10L, null);

        ReplayChatModel model = new ReplayChatModel(ModelReplay.load(dir.resolve("recordings.jsonl")));
        ChatResponse response = model.call(systemUserPrompt("审系统", "审用户"));

        assertEquals("通过", WriterAgent.extractText(response));
        assertEquals(555L, WriterAgent.usageOf(response), "token 走 usage 元数据回流账本埋点");
    }

    @Test
    void 回放ChatModel_未命中快速失败不给编造() {
        ModelCallLog callLog = recordingCallLog();
        callLog.record("PLANNER", requestOf(systemUserPrompt("有录制", "有录制用户")),
                responseOf("ok", 100L), 10L, null);

        ReplayChatModel model = new ReplayChatModel(ModelReplay.load(dir.resolve("recordings.jsonl")));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> model.call(systemUserPrompt("没有录制", "没有录制用户")));
        // 报错信息带键哈希与 prompt 前缀：可定位缺失录制
        assertTrue(error.getMessage().contains("key="));
        assertTrue(error.getMessage().contains("回放未命中"));
    }

    @Test
    void 同键多条录制取首条_保证确定性() {
        ModelCallLog callLog = recordingCallLog();
        callLog.record("EXTRACTION", requestOf(systemUserPrompt("抽取", "同输入")),
                responseOf("第一稿", 100L), 10L, null);
        callLog.record("EXTRACTION", requestOf(systemUserPrompt("抽取", "同输入")),
                responseOf("第二稿", 200L), 10L, null);

        ModelReplay replay = ModelReplay.load(dir.resolve("recordings.jsonl"));
        assertEquals(1, replay.size());
        assertEquals("第一稿", replay.find("抽取", "同输入").response());
    }
}
