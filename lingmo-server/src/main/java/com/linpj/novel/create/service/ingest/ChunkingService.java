package com.linpj.novel.create.service.ingest;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 段落切块服务（WIKI_RAG_UNIFIED_PLAN §4.2：段落/场景粒度 300–500 字，重叠 50）
 *
 * <p>确定性纯函数（无模型）：按空行/换行切段，短段合并、长段硬切，
 * 相邻块带 50 字重叠保证跨块语义连续。token 估算按中文 1 字 ≈ 1 token 折算。
 *
 * @author HL
 */
@Service
public class ChunkingService {

    /** 目标块长度（字符） */
    static final int TARGET_SIZE = 400;

    /** 块长硬上限（字符） */
    static final int MAX_SIZE = 500;

    /** 相邻块重叠（字符） */
    static final int OVERLAP = 50;

    /** 单块最小长度（短于此并入相邻块） */
    static final int MIN_SIZE = 120;

    /**
     * @return 切块结果（seq 从 0 递增；空文本返回空列表）
     */
    public List<Chunk> chunk(String text) {
        List<Chunk> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalized = text.replace("\r\n", "\n").strip();
        // 1. 段落整理：超长段落先硬切（保句读优先）
        List<String> paragraphs = new ArrayList<>();
        for (String para : normalized.split("\n+")) {
            String p = para.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > MAX_SIZE) {
                paragraphs.addAll(hardSplit(p));
            } else {
                paragraphs.add(p);
            }
        }
        // 2. 聚合到 [TARGET_SIZE, MAX_SIZE] 区间
        List<String> merged = merge(paragraphs);
        // 3. 生成重叠块
        int seq = 0;
        for (int i = 0; i < merged.size(); i++) {
            String content = merged.get(i);
            if (i > 0) {
                String prev = merged.get(i - 1);
                int overlapLen = Math.min(OVERLAP, Math.min(prev.length(), content.length()));
                if (overlapLen > 0 && prev.length() > overlapLen) {
                    content = prev.substring(prev.length() - overlapLen) + content;
                }
            }
            result.add(new Chunk(content, seq++));
        }
        return result;
    }

    /** 短段顺次合并，到目标长度即收块 */
    private static List<String> merge(List<String> paragraphs) {
        List<String> merged = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String para : paragraphs) {
            if (buf.length() > 0 && buf.length() + para.length() > TARGET_SIZE) {
                merged.add(buf.toString());
                buf.setLength(0);
            }
            if (buf.length() > 0) {
                buf.append('\n');
            }
            buf.append(para);
            if (buf.length() >= TARGET_SIZE) {
                merged.add(buf.toString());
                buf.setLength(0);
            }
        }
        if (buf.length() > 0) {
            if (buf.length() < MIN_SIZE && !merged.isEmpty()) {
                // 尾块过短：并入上一块（若不超硬上限）
                int last = merged.size() - 1;
                String candidate = merged.get(last) + "\n" + buf;
                if (candidate.length() <= MAX_SIZE) {
                    merged.set(last, candidate);
                } else {
                    merged.add(buf.toString());
                }
            } else {
                merged.add(buf.toString());
            }
        }
        return merged;
    }

    /** 超长段硬切：优先按句末标点收块 */
    private static List<String> hardSplit(String paragraph) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int lastSentenceEnd = -1;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；') {
                lastSentenceEnd = i + 1;
            }
            if (i - start + 1 >= TARGET_SIZE) {
                int cut = lastSentenceEnd > start ? lastSentenceEnd : start + MAX_SIZE;
                if (cut <= start) {
                    cut = start + TARGET_SIZE;
                }
                parts.add(paragraph.substring(start, cut));
                start = cut;
                lastSentenceEnd = -1;
            }
        }
        if (start < paragraph.length()) {
            parts.add(paragraph.substring(start));
        }
        return parts;
    }

    /**
     * 切块结果
     *
     * @param content    块文本（含与前块的重叠）
     * @param seq        块序号（source 内递增）
     */
    public record Chunk(String content, int seq) {

        /** token 估算（中文 1 字 ≈ 1 token 折算） */
        public int tokenCount() {
            return content.length();
        }
    }
}
