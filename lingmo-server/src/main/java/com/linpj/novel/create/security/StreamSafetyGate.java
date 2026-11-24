package com.linpj.novel.create.security;

import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 中置流式安全（v2.0 M4-E2，设计 4.6 安全三层：PRE / STREAM / POST 的 STREAM 层）
 *
 * 职责边界：
 * 1. 判定：复用 {@link SensitiveTerms} 同一份词表（与 POST 层同口径，不搞两套标准）
 * 2. 断流：命中即断（{@link StreamGuard}），客户端看不到命中词
 * 3. 恢复：把命中转成定向修订清单交给 Writer 截断重生成（一次为限）——
 *    与 POST 层同一份修复指令：只改表达，不动情节、不压缩篇幅
 * 4. 落账：novel_safety_log.layer=STREAM，action ∈ {REWRITE, REJECT}
 *    （PASS 由 POST 层按章记录，STREAM 只记真正发生拦截的事件，避免分母重复）
 *
 * 与 POST 的分工：STREAM 追求"别让违规字面到读者眼前"，POST 负责"整章兜底 + 人审队列"。
 * 一层失败不豁免另一层：STREAM 拦截成功仍要过 POST gate（重生成稿同样可能命中）。
 *
 * @author HL
 */
@Service
public class StreamSafetyGate {

    /** 中置层标识（novel_safety_log.layer） */
    public static final String LAYER = "STREAM";

    /** 命中 -&gt; 已截断重生成（拦截率分子） */
    public static final String ACTION_REWRITE = "REWRITE";

    /** 命中 -&gt; 无法重生成或重生成后仍命中（任务失败，转后置/人审） */
    public static final String ACTION_REJECT = "REJECT";

    private final SensitiveTerms terms;
    private final SafetyAuditService audit;

    /** audit 允许为 null（单测/轻量装配：只判定不落库） */
    public StreamSafetyGate(SensitiveTerms terms, SafetyAuditService audit) {
        this.terms = terms;
        this.audit = audit;
    }

    /** 新建一次写入用的扫描器（同一次写入的增量必须共用同一实例才能识别跨包词） */
    public StreamGuard newGuard() {
        return new StreamGuard(terms);
    }

    /** 定向修订清单（复用 POST 层同一份指令，避免两层给出冲突的改写要求） */
    public ReviewReport repairReport(List<SensitiveTerms.Hit> hits) {
        return FinalDraftGate.repairReport(hits);
    }

    /** 截断重生成（一次为限） */
    public void recordRewrite(Long bookId, Long chapterId, List<SensitiveTerms.Hit> hits, int forwardedLength) {
        record(bookId, FinalDraftGate.ACTION_REWRITE, chapterId, hits, forwardedLength);
    }

    /** 无法恢复：重生成后仍命中 / 无可重生成内容（任务失败） */
    public void recordReject(Long bookId, Long chapterId, List<SensitiveTerms.Hit> hits, int forwardedLength) {
        record(bookId, ACTION_REJECT, chapterId, hits, forwardedLength);
    }

    private void record(Long bookId, String action, Long chapterId,
                        List<SensitiveTerms.Hit> hits, int forwardedLength) {
        if (audit == null) {
            return;
        }
        try {
            // 只落定位与摘要，不落正文（与 POST 层同一隐私口径）
            String detail = "chapterId=" + chapterId + ";forwardedLen=" + forwardedLength
                    + ";hits=" + FinalDraftGate.describe(hits);
            audit.record(bookId, LAYER, FinalDraftGate.categoryOf(hits), action, detail);
        } catch (Exception ignored) {
            // 审计通道故障不改变拦截决策（拦截已发生，正文不会因落账失败而下发）
        }
    }
}
