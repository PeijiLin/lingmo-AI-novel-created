package com.linpj.novel.create.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 流式增量扫描器（v2.0 M4-E2，设计 4.6 安全三层的中置 STREAM 层）
 *
 * 语义：**命中即断**——命中的增量在下发之前拦下并抛出 {@link StreamSensitiveHitException}，
 * 客户端永远看不到命中词本身；已下发的部分由调用方发 REVISION 事件作废后重生成。
 *
 * 为什么不能"逐包扫一遍就放行"：词会被 SSE 分包切断（"制" / "毒工艺"），
 * 逐包扫描必漏。这里保留末尾 {@code maxTermLength()-1} 个字符不下发，
 * 让跨包词有机会在新包到达时被整体看到——代价是正文尾巴最多晚一个包下发，
 * 由 {@link #flush(Consumer)} 在正常收尾时补齐。
 *
 * 边界（诚实声明）：只拦字面命中；谐音/隐语/改写不在此层能力内（那是后置 gate + 人审的事）。
 * 本类是纯对象（无 Spring 依赖、无 IO），可单测到分包粒度。
 *
 * @author HL
 */
public final class StreamGuard {

    private final SensitiveTerms terms;

    /** 未下发的尾巴（长度 <= window），等待后续增量补齐跨包词 */
    private final StringBuilder pending = new StringBuilder();

    /** 已下发的正文（命中时作为截断点回传） */
    private final StringBuilder forwarded = new StringBuilder();

    private final int window;

    /** 已触发：此后任何增量都不再下发（防止异常被上层吞掉后继续泄流） */
    private boolean tripped;

    public StreamGuard(SensitiveTerms terms) {
        this.terms = terms;
        this.window = Math.max(0, terms == null ? 0 : terms.maxTermLength() - 1);
    }

    /** 包装下游消费者（SSE content 下发） */
    public Consumer<String> guarded(Consumer<String> downstream) {
        Objects.requireNonNull(downstream, "downstream 不可为空");
        return chunk -> accept(chunk, downstream);
    }

    private void accept(String chunk, Consumer<String> downstream) {
        if (tripped) {
            throw new StreamSensitiveHitException(List.of(), forwarded.toString());
        }
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        pending.append(chunk);
        List<SensitiveTerms.Hit> hits = scan(pending.toString(), forwarded.length());
        if (!hits.isEmpty()) {
            tripped = true;
            throw new StreamSensitiveHitException(hits, forwarded.toString());
        }
        if (pending.length() > window) {
            int cut = pending.length() - window;
            String out = pending.substring(0, cut);
            pending.delete(0, cut);
            forwarded.append(out);
            downstream.accept(out);
        }
    }

    /**
     * 正常收尾：把尾巴下发。
     * 尾巴在最后一次 {@link #accept} 时已被扫过，这里再扫一遍只是防御（宁可晚发不可漏发）。
     */
    public void flush(Consumer<String> downstream) {
        if (tripped || pending.length() == 0) {
            return;
        }
        List<SensitiveTerms.Hit> hits = scan(pending.toString(), forwarded.length());
        if (!hits.isEmpty()) {
            tripped = true;
            throw new StreamSensitiveHitException(hits, forwarded.toString());
        }
        String tail = pending.toString();
        pending.setLength(0);
        forwarded.append(tail);
        downstream.accept(tail);
    }

    /** 已下发的正文（命中后作为"截断重生成"的原稿） */
    public String forwardedText() {
        return forwarded.toString();
    }

    /** 是否已触发拦截（触发后 guard 不可复用，调用方应换新实例） */
    public boolean tripped() {
        return tripped;
    }

    /** 扫描并把 offset 校正到全文坐标（pending 起点 = 已下发长度） */
    private List<SensitiveTerms.Hit> scan(String text, int base) {
        if (terms == null) {
            return List.of();
        }
        List<SensitiveTerms.Hit> hits = terms.scan(text);
        if (hits.isEmpty() || base == 0) {
            return hits;
        }
        List<SensitiveTerms.Hit> shifted = new ArrayList<>(hits.size());
        for (SensitiveTerms.Hit hit : hits) {
            shifted.add(new SensitiveTerms.Hit(hit.term(), hit.category(), base + hit.offset()));
        }
        return shifted;
    }

    /** 便于日志/单测的可读摘要 */
    String describeState() {
        return String.format(Locale.ROOT, "forwarded=%d,pending=%d,tripped=%s",
                forwarded.length(), pending.length(), tripped);
    }
}
