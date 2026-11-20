package com.linpj.novel.create.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 模型回放器：从 JSONL 录制建索引，按 (system, user) 内容哈希回放固定响应
 * （v2.0 设计 6.1：录制回放一等测试能力——隔离模型随机性，实验可复现）
 *
 * - 键 = sha256(system + '\\u241F' + user)：录制与回放经 PromptTexts 同一抽取与截断口径
 * - 同键多条录制只取首条（确定性要求；多温度/多次采样需在录制侧隔离为不同文件）
 * - error / 无响应条目跳过（失败样本进 S1 探针统计，不参与回放）
 *
 * @author HL
 */
public final class ModelReplay {

    private static final Logger log = LoggerFactory.getLogger(ModelReplay.class);

    /** 对外暴露的可回放条目（不含 system/user 全文，避免污染消费端内存） */
    public record RecordedCall(String agent, String response, long tokens, String model) {
    }

    /** 索引内部条目（携带键计算所需的 system/user 文本） */
    private record Entry(String system, String user, String agent,
                         String response, long tokens, String model) {
    }

    private final Map<String, Entry> index;

    private ModelReplay(Map<String, Entry> index) {
        this.index = index;
    }

    /**
     * 加载 JSONL 录制文件（文件缺失返回空回放器并告警，便于测试占位）
     */
    public static ModelReplay load(Path jsonl) {
        Map<String, Entry> index = new HashMap<>();
        if (jsonl == null || !Files.exists(jsonl)) {
            log.warn("回放文件不存在: {}", jsonl);
            return new ModelReplay(index);
        }
        int skipped = 0;
        try {
            List<String> lines = Files.readAllLines(jsonl, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                Entry entry = parse(line);
                if (entry == null) {
                    skipped++;
                    continue;
                }
                index.putIfAbsent(keyOf(entry.system(), entry.user()), entry);
            }
        } catch (IOException e) {
            throw new IllegalStateException("回放文件读取失败: " + jsonl, e);
        }
        log.info("回放索引加载完成: size={}, skipped={}", index.size(), skipped);
        return new ModelReplay(index);
    }

    /**
     * 解析单行：失败调用（无响应或带 error）与坏行返回 null，不参与回放
     */
    private static Entry parse(String line) {
        try {
            JsonObject entry = JsonParser.parseString(line).getAsJsonObject();
            if (entry.get("response") == null || entry.get("response").isJsonNull()) {
                return null;
            }
            if (entry.get("error") != null && !entry.get("error").isJsonNull()) {
                return null;
            }
            return new Entry(
                    text(entry, "system"),
                    text(entry, "user"),
                    text(entry, "agent"),
                    entry.get("response").getAsString(),
                    entry.get("tokens").getAsLong(),
                    text(entry, "model"));
        } catch (Exception e) {
            return null; // 坏行跳过：单行脏数据不毁掉整个回放
        }
    }

    private static String text(JsonObject entry, String field) {
        return entry.get(field) == null || entry.get(field).isJsonNull() ? null : entry.get(field).getAsString();
    }

    /**
     * 回放键：system 与 user 内容哈希（null 归一为空串；截断口径由录制侧 PromptTexts 保证一致）
     */
    public static String keyOf(String system, String user) {
        return sha256(nullSafe(system) + "␟" + nullSafe(user));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // SHA-256 标准算法必然存在；防御性退化
            return String.valueOf(content.hashCode());
        }
    }

    public int size() {
        return index.size();
    }

    public boolean has(String system, String user) {
        return index.containsKey(keyOf(system, user));
    }

    /**
     * 按声明的 (system, user) 查找回放条目；未命中返回 null
     */
    public RecordedCall find(String system, String user) {
        Entry entry = index.get(keyOf(system, user));
        return entry == null ? null : toPublic(entry);
    }

    /**
     * 回放热路径：直接以 Prompt 计算 (system, user) 后查询（键抽取与录制同源）
     */
    public RecordedCall find(org.springframework.ai.chat.prompt.Prompt prompt) {
        return find(PromptTexts.systemOf(prompt), PromptTexts.userOf(prompt));
    }

    private static RecordedCall toPublic(Entry entry) {
        return new RecordedCall(entry.agent(), entry.response(), entry.tokens(), entry.model());
    }
}
