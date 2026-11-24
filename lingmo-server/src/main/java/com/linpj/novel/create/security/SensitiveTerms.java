package com.linpj.novel.create.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 内容词表（v2.0 M4-E3 后置 gate 与 E2 中置流式拦截共用同一份判定数据）
 *
 * 设计要点：
 * - 词表是**数据**不是开关：安全层不可让渡（P6），词表为空即无命中（= 现状行为），
 *   不存在"把词表关掉"的运行时配置
 * - 内置基线 + 配置追加（novel.safety.wordlist.categories.XXX[0]=词）：基线是发布底线，
 *   部署环境可按品类收紧，不能放宽到 0
 * - 判定确定性：纯字符串匹配，同输入同命中清单（可解释、可复现，符合公理 3）
 * - 双语/大小写：统一小写后匹配（中文无影响，英文词不因大小写漏检）
 *
 * 局限（诚实声明）：词表只能覆盖"显式字面"命中，对隐语/谐音/改写无效——
 * 这正是保留误杀率指标 + 修复式改写（而非硬拒绝）的原因，词表不承担语义判定职责。
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.safety.wordlist")
public class SensitiveTerms {

    /** 单条命中（offset 为原文 0 基字符下标，供人工定位复核） */
    public record Hit(String term, String category, int offset) {
    }

    /** 分类 → 词表（与内置基线合并：同分类追加） */
    private Map<String, List<String>> categories = new LinkedHashMap<>();

    /** 展平检索表（配置变更后重建；词表很小，无需前缀树） */
    private volatile List<Entry> index;

    private record Entry(String term, String category) {
    }

    public Map<String, List<String>> getCategories() {
        return categories;
    }

    public void setCategories(Map<String, List<String>> categories) {
        this.categories = categories == null ? new LinkedHashMap<>() : categories;
        this.index = null;
    }

    /**
     * 内置基线词表（发布底线，不可通过配置清空）。
     * 分类即 novel_safety_log.category 的可读后缀，便于按风险类型统计拦截/误杀。
     */
    static Map<String, List<String>> baseline() {
        Map<String, List<String>> base = new LinkedHashMap<>();
        base.put("SEXUAL", List.of("性交", "口交", "性高潮"));
        base.put("VIOLENCE", List.of("肢解", "开膛破肚", "剥皮抽筋"));
        base.put("ILLEGAL", List.of("制毒工艺", "炸弹制作", "枪支改装"));
        base.put("POLITICS", List.of("颠覆国家政权", "分裂国家"));
        return base;
    }

    /** 最长词长度（流式增量窗口需 ≥ 最长词才能跨包命中） */
    public int maxTermLength() {
        int max = 0;
        for (Entry entry : index()) {
            max = Math.max(max, entry.term().length());
        }
        return max;
    }

    /**
     * 扫描正文：返回全部命中（按位置升序，同位置长词优先）。
     * 同一位置不重复计入同词（避免长文反复计数失真）。
     */
    public List<Hit> scan(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String haystack = text.toLowerCase(Locale.ROOT);
        List<Hit> hits = new ArrayList<>();
        for (Entry entry : index()) {
            String needle = entry.term().toLowerCase(Locale.ROOT);
            int from = 0;
            while (true) {
                int at = haystack.indexOf(needle, from);
                if (at < 0) {
                    break;
                }
                hits.add(new Hit(entry.term(), entry.category(), at));
                from = at + needle.length();
            }
        }
        hits.sort(Comparator.comparingInt(Hit::offset)
                .thenComparing(hit -> -hit.term().length()));
        return hits;
    }

    /** 展平（基线在前、配置在后；同词以先出现分类为准，配置只做追加不做覆盖） */
    private List<Entry> index() {
        List<Entry> cached = index;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (index != null) {
                return index;
            }
            // 基线值为不可变列表：逐个复制成可变列表再追加配置（否则 addAll 抛 UnsupportedOperation）
            Map<String, List<String>> merged = new LinkedHashMap<>();
            baseline().forEach((category, terms) -> {
                if (category == null || terms == null) {
                    return;
                }
                merged.put(category.trim().toUpperCase(Locale.ROOT), new ArrayList<>(terms));
            });
            categories.forEach((category, terms) -> {
                if (category == null || category.isBlank() || terms == null) {
                    return;
                }
                merged.computeIfAbsent(category.trim().toUpperCase(Locale.ROOT),
                        key -> new ArrayList<>()).addAll(terms);
            });
            List<Entry> entries = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            merged.forEach((category, terms) -> terms.stream()
                    .filter(term -> term != null && !term.isBlank())
                    .map(String::trim)
                    .map(term -> new Entry(term, category))
                    .filter(entry -> seen.add(entry.term()))
                    .forEach(entries::add));
            index = List.copyOf(entries);
            return index;
        }
    }
}
