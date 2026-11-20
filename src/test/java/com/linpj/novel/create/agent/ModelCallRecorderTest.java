package com.linpj.novel.create.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.linpj.novel.create.config.PipelineRecordingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模型调用录制单元测试（v2.0 设计 6.1：禁用零副作用；启用记录完整调用；失败也记录）
 *
 * @author HL
 */
class ModelCallRecorderTest {

    /** 临时目录（.temp 下按用例隔离；@TempDir 在 Windows 常因进程占用清理失败，改用尽力清理） */
    private Path dir;

    @AfterEach
    void cleanUp() {
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir.getParent())
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 文件被占用时容忍残留（.temp 区，gitignored）
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private PipelineRecordingProperties props(boolean enabled) {
        dir = Paths.get(".temp", "test-recordings", UUID.randomUUID().toString());
        PipelineRecordingProperties properties = new PipelineRecordingProperties();
        properties.setEnabled(enabled);
        properties.setDir(dir.toString());
        return properties;
    }

    private ChatClientRequest systemUserRequest() {
        List<Message> messages = List.of(new SystemMessage("系统提示文本"), new UserMessage("用户提示文本"));
        return ChatClientRequest.builder()
                .prompt(new Prompt(messages))
                .context(new HashMap<>())
                .build();
    }

    private ChatClientResponse response(String text) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(new HashMap<>())
                .build();
    }

    @Test
    void 禁用录制_零副作用透传() {
        ModelCallLog callLog = new ModelCallLog(props(false));
        ModelCallRecorder recorder = new ModelCallRecorder("PLANNER", callLog);
        ChatClientResponse expected = response("ok");
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(expected);

        ChatClientResponse actual = recorder.adviseCall(systemUserRequest(), chain);

        assertSame(expected, actual, "透传原响应");
        assertFalse(Files.exists(dir), "禁用时不得创建任何文件");
    }

    @Test
    void 启用录制_成功调用记录完整字段() throws IOException {
        ModelCallLog callLog = new ModelCallLog(props(true));
        ModelCallRecorder recorder = new ModelCallRecorder("CRITIC", callLog);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(response("审查通过"));

        ChatClientResponse returned = recorder.adviseCall(systemUserRequest(), chain);
        assertNotNull(returned);

        Path file = dir.resolve("recordings.jsonl");
        assertTrue(Files.exists(file), "JSONL 已落盘");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        JsonObject entry = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("CRITIC", entry.get("agent").getAsString());
        assertEquals("系统提示文本", entry.get("system").getAsString());
        assertEquals("用户提示文本", entry.get("user").getAsString());
        assertEquals("审查通过", entry.get("response").getAsString());
        assertNotNull(entry.get("ts"));
        assertNotNull(entry.get("elapsedMs"));
        assertTrue(entry.get("error").isJsonNull(), "成功调用 error 为 null");
    }

    @Test
    void 启用录制_失败调用记录error且原样抛出() throws IOException {
        ModelCallLog callLog = new ModelCallLog(props(true));
        ModelCallRecorder recorder = new ModelCallRecorder("EXTRACTION", callLog);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenThrow(new IllegalStateException("网关超时"));

        ChatClientRequest request = systemUserRequest();
        assertThrows(IllegalStateException.class, () -> recorder.adviseCall(request, chain));

        Path file = dir.resolve("recordings.jsonl");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size());
        JsonObject entry = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("EXTRACTION", entry.get("agent").getAsString());
        assertTrue(entry.get("error").getAsString().contains("网关超时"));
        assertTrue(entry.get("response").isJsonNull(), "失败调用 response 为 null");
    }

    @Test
    void 同时多次调用_追加写入互不覆盖() throws IOException {
        ModelCallLog callLog = new ModelCallLog(props(true));
        ModelCallRecorder recorder = new ModelCallRecorder("PLANNER", callLog);
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(response("稿一"), response("稿二"));

        recorder.adviseCall(systemUserRequest(), chain);
        recorder.adviseCall(systemUserRequest(), chain);

        List<String> lines = Files.readAllLines(dir.resolve("recordings.jsonl"), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals("稿一", JsonParser.parseString(lines.get(0)).getAsJsonObject().get("response").getAsString());
        assertEquals("稿二", JsonParser.parseString(lines.get(1)).getAsJsonObject().get("response").getAsString());
        // Mockito 交互完整性自检
        Mockito.verify(chain, Mockito.times(2)).nextCall(any());
    }
}
