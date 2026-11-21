package com.linpj.novel.create.security;

import java.util.List;

/**
 * 流式敏感命中（v2.0 M4-E2）
 *
 * 抛出点是**外发之前**：命中的那一段增量不会下发给客户端，已经下发的部分记录在
 * {@link #partialText()} 里，供"截断重生成"作为待修订原稿（也作为误杀复核时的事实依据）。
 *
 * @author HL
 */
public class StreamSensitiveHitException extends RuntimeException {

    private final List<SensitiveTerms.Hit> hits;

    /** 命中点之前已经下发给客户端的正文（截断点，可能为空串） */
    private final String partialText;

    public StreamSensitiveHitException(List<SensitiveTerms.Hit> hits, String partialText) {
        super("流式内容命中词表：" + FinalDraftGate.describe(hits));
        this.hits = hits == null ? List.of() : List.copyOf(hits);
        this.partialText = partialText == null ? "" : partialText;
    }

    public List<SensitiveTerms.Hit> hits() {
        return hits;
    }

    public String partialText() {
        return partialText;
    }
}
