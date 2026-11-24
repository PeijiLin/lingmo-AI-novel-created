package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import com.linpj.novel.create.utils.JsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * evidenceId 编解码（自主性改造 R1b：§11.1-M1 引文协议核心）
 *
 * <p>三段式设计（回执存原文、模型文本带 id、重放纯函数重打 id）：
 * <ol>
 *   <li><b>注入 wrap</b>：工具返回结构化条目（List&lt;Map&gt;）时，Registry 落回执
 *       （存<b>原文</b>——原始事实，不带指针）拿到 receiptId 后，给每条注入
 *       {@code evidenceId=ev:<receiptId>#<序号>}（1 起）再喂模型——引文地址的生成权
 *       在代码侧，模型 finish 只需引用（引文有效率上限从"模型能力"变"工具可回显性"）；</li>
 *   <li><b>重放 rewrap</b>：kill-resume 重放时对回执原文按同一纯函数重打 id——
 *       模型历史文本与不中断运行<b>逐字节一致</b>（E3 序列不变式不因引文协议破坏）；</li>
 *   <li><b>校验 verify</b>：确定性存在性校验——receipt 存在 + 序号界内 +
 *       引文文本与回执条目一致（归一化包含）。无任何模型参与。</li>
 * </ol>
 *
 * <p>非结构化结果（纯文本回执）不注入、不参与引文——wrap 透传原样。
 *
 * @author HL
 */
public final class EvidenceCodec {

    public static final String EVIDENCE_ID_FIELD = "evidenceId";
    static final String ID_PREFIX = "ev:";

    private EvidenceCodec() {
    }

    /**
     * 注入 evidenceId（喂模型文本）。
     *
     * @param items 结构化条目（List&lt;Map&gt;；非 List 原样字符串化——透传）
     * @return 带证据指针的 JSON 文本
     */
    public static String wrap(long receiptId, Object items) {
        List<Map<String, Object>> entries = asEntries(items);
        if (entries == null) {
            return items == null ? "（空结果）" : String.valueOf(items);
        }
        if (entries.isEmpty()) {
            return JsonUtil.toJson(List.of());
        }
        List<Map<String, Object>> wrapped = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.putAll(entries.get(i) == null ? Map.of() : entries.get(i));
            entry.put(EVIDENCE_ID_FIELD, format(receiptId, i + 1));
            wrapped.add(entry);
        }
        return JsonUtil.toJson(wrapped);
    }

    /**
     * 重放重打 id（回执原文 → 模型历史文本）。
     * 与 wrap 同一纯函数：非 JSON 数组原文原样返回（非证据回执不注入）。
     */
    public static String rewrap(long receiptId, String receiptResult) {
        if (receiptResult == null || receiptResult.isBlank()) {
            return "（空结果）";
        }
        String trimmed = receiptResult.trim();
        if (!trimmed.startsWith("[")) {
            return receiptResult; // 非结构化回执（纯文本/错误摘要）原样
        }
        try {
            List<?> raw = JsonUtil.fromJson(trimmed, List.class);
            return wrap(receiptId, raw);
        } catch (Exception e) {
            return receiptResult; // 原文不可解析（防御）：原样，不阻断重放
        }
    }

    /** evidenceId 格式：ev:<receiptId>#<序号>（序号从 1 起）；非法返回 null */
    public static long[] parse(String evidenceId) {
        if (evidenceId == null) {
            return null;
        }
        String trimmed = evidenceId.trim();
        if (!trimmed.startsWith(ID_PREFIX) || !trimmed.contains("#")) {
            return null;
        }
        try {
            String body = trimmed.substring(ID_PREFIX.length());
            String[] parts = body.split("#", 2);
            long receiptId = Long.parseLong(parts[0].trim());
            int index = Integer.parseInt(parts[1].trim());
            return receiptId > 0 && index > 0 ? new long[]{receiptId, index} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 引文格式化（注入与解析同源，防格式漂移） */
    public static String format(long receiptId, int index) {
        return ID_PREFIX + receiptId + "#" + index;
    }

    /**
     * 确定性引文校验（R1b 硬校验：citation.verify 的代码实现）。
     *
     * @param evidenceId finish 引用的证据地址
     * @param claimText  引文文本（issue.evidence / claim.text）
     * @param receipt    回执行（null = 回执不存在，直接失败）
     */
    public static EvidenceCheck verify(String evidenceId, String claimText, NovelToolReceipt receipt) {
        long[] parts = parse(evidenceId);
        if (parts == null) {
            return new EvidenceCheck(false, "evidenceId 格式非法: " + evidenceId);
        }
        long receiptId = parts[0];
        int index = (int) parts[1];
        if (receipt == null || receipt.getId() == null || receipt.getId() != receiptId) {
            return new EvidenceCheck(false, "回执不存在: " + format(receiptId, index));
        }
        List<Map<String, Object>> entries = asEntries(receipt.getResult());
        if (entries == null) {
            return new EvidenceCheck(false, "回执非结构化证据（无条目可回验）: " + evidenceId);
        }
        if (index > entries.size()) {
            return new EvidenceCheck(false, "序号越界: " + evidenceId + "（回执共 " + entries.size() + " 条）");
        }
        Map<String, Object> entry = entries.get(index - 1);
        if (claimText == null || claimText.isBlank()) {
            return new EvidenceCheck(false, "引文文本为空: " + evidenceId);
        }
        if (!textMatches(claimText, entry)) {
            return new EvidenceCheck(false, "引文文本与回执条目不一致: " + evidenceId);
        }
        return new EvidenceCheck(true, null);
    }

    /**
     * 文本一致性（冒烟校准后口径）：
     * 条目递归展开到叶子字符串值（引用发生在字段值级，非对象级——fact 对象序列化
     * 含 id/时间戳噪音，对象级互含会把合法引用全部误杀）；claim 与任一叶子归一化后
     * 最长公共子串 ≥ EVIDENCE_LCS_THRESHOLD 即命中——容忍措辞改写（真实冒烟：模型
     * 复合引文含"左臂中弹（第12章），恢复期，抬举困难"切片 ✓），拦截不相干幻觉
     * （"右臂完全健康" vs "左臂中弹…" LCS≈1 ✗）。阈值待 S 系实验校准。
     */
    private static boolean textMatches(String claimText, Map<String, Object> entry) {
        String normalizedClaim = normalize(claimText);
        if (normalizedClaim.isEmpty()) {
            return false;
        }
        java.util.List<String> leaves = new java.util.ArrayList<>();
        collectLeafValues(entry, leaves);
        for (String leaf : leaves) {
            String normalizedLeaf = normalize(leaf);
            if (normalizedLeaf.length() >= 2 && longestCommonSubstring(normalizedClaim, normalizedLeaf)
                    >= EVIDENCE_LCS_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /** 递归收集叶子字符串值（嵌套 Map/List 下钻；evidenceId 字段跳过——指针本身不是内容） */
    @SuppressWarnings("unchecked")
    private static void collectLeafValues(Object value, java.util.List<String> leaves) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (EVIDENCE_ID_FIELD.equals(entry.getKey())) {
                    continue;
                }
                collectLeafValues(entry.getValue(), leaves);
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                collectLeafValues(item, leaves);
            }
        } else if (value instanceof Number || value instanceof Boolean) {
            // 数字/布尔叶子含语义密度低，跳过（防 id/时间戳碎片误配）
        } else {
            leaves.add(String.valueOf(value));
        }
    }

    /** 最长公共子串（动态规划；两串各 ≤ 数百字符，性能可忽略） */
    private static int longestCommonSubstring(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int max = 0;
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                curr[j] = a.charAt(i - 1) == b.charAt(j - 1) ? prev[j - 1] + 1 : 0;
                if (curr[j] > max) {
                    max = curr[j];
                }
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return max;
    }

    /** 引文一致阈值（归一化后公共子串长度；4=中文关键词级下限——"左臂中弹"类改写引用容忍，"臂中弹"3 字幻觉拦截，S 系实验校准） */
    static final int EVIDENCE_LCS_THRESHOLD = 4;

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asEntries(Object items) {
        if (items instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map) {
                    out.add((Map<String, Object>) item);
                } else if (item != null) {
                    // 字符串条目（如章级摘要文本）包装为单字段条目
                    out.add(Map.of("text", String.valueOf(item)));
                }
            }
            return out;
        }
        if (items instanceof String json && json.trim().startsWith("[")) {
            try {
                return asEntries(JsonUtil.fromJson(json.trim(), List.class));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /** 引文校验结果 */
    public record EvidenceCheck(boolean ok, String violations) {
    }
}
