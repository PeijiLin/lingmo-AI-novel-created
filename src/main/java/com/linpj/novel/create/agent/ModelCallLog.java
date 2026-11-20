package com.linpj.novel.create.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.linpj.novel.create.config.PipelineRecordingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型调用录制器：JSONL 落盘，作为回放与 POC 探针（S1/S4/S6）的数据源
 *
 * - 落盘字段：ts / agent / system / user / model / tokens / response / elapsedMs / error
 *   —— 含输入哈希所需的完整 prompt 与响应文本（书稿正文属本地实验数据，目录默认 .temp/）
 * - 写盘失败仅告警不中断管道（录制是观测能力，不参与主链路）
 * - 线程安全：单文件追加写 + 对象锁（Agent 并发调用量级低，无需无锁结构）
 *
 * @author HL
 */
@Component
public class ModelCallLog {

    private static final Logger log = LoggerFactory.getLogger(ModelCallLog.class);

    private static final String FILE_NAME = "recordings.jsonl";

    private final PipelineRecordingProperties properties;
    private final Object fileLock = new Object();
    // serializeNulls：每行字段集稳定（失败调用也有 response:null/error:...），
    // 回放与探针消费端按固定 schema 解析，无需逐行判空键
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public ModelCallLog(PipelineRecordingProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * 记录一次模型调用（成功或失败）。禁用时零副作用。
     *
     * @param agent     调用方标识（PLANNER / CRITIC / POLISHER / EXTRACTION）
     * @param request   请求（含最终 prompt；null 安全）
     * @param response  响应（失败调用为 null）
     * @param elapsedMs 耗时
     * @param error     失败原因（成功为 null）
     */
    public void record(String agent, ChatClientRequest request, ChatClientResponse response,
                       long elapsedMs, Exception error) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            String line = gson.toJson(entry(agent, request, response, elapsedMs, error));
            synchronized (fileLock) {
                Path dir = Paths.get(properties.getDir());
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(FILE_NAME), line + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            // 录制是观测能力：任何失败都不影响创作主链路
            log.warn("模型调用录制失败(不中断管道): {}", e.getMessage());
        }
    }

    /** 组装单行 JSON（字段顺序稳定，便于 jq/awk 消费） */
    private Map<String, Object> entry(String agent, ChatClientRequest request,
                                      ChatClientResponse response, long elapsedMs, Exception error) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ts", Instant.now().toString());
        map.put("agent", agent);
        map.put("system", textOf(request, MessageType.SYSTEM));
        map.put("user", textOf(request, MessageType.USER));
        map.put("model", modelOf(response));
        map.put("tokens", response == null ? 0L : WriterAgent.usageOf(response.chatResponse()));
        map.put("response", response == null ? null : WriterAgent.extractText(response.chatResponse()));
        map.put("elapsedMs", elapsedMs);
        map.put("error", error == null ? null : error.toString());
        return map;
    }

    /** 按消息类型抽取 prompt 文本（记忆 advisor 注入的历史消息一并记录，多段以空行连接） */
    private static String textOf(ChatClientRequest request, MessageType type) {
        if (request == null || request.prompt() == null) {
            return null;
        }
        List<Message> instructions = request.prompt().getInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return null;
        }
        String joined = instructions.stream()
                .filter(m -> m != null && m.getMessageType() == type && m.getText() != null)
                .map(Message::getText)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse(null);
        // 超长截断保护：单字段上限 64k 字符，极端长 prompt 不至于撑爆日志文件
        if (joined != null && joined.length() > 64_000) {
            return joined.substring(0, 64_000) + "...(truncated)";
        }
        return joined;
    }

    /** 模型标识（响应元数据；网关不回传时为 null） */
    private static String modelOf(ChatClientResponse response) {
        try {
            if (response != null && response.chatResponse() != null
                    && response.chatResponse().getMetadata() != null) {
                return response.chatResponse().getMetadata().getModel();
            }
        } catch (Exception ignored) {
            // 防御：元数据实现不回传时不影响录制
        }
        return null;
    }
}
