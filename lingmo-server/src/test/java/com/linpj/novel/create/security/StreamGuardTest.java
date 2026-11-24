package com.linpj.novel.create.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 中置流式拦截单元测试（M4-E2 / TC-38）
 *
 * 验收要点（设计 4.6 STREAM 层）：
 * 1. 命中词**在下发之前**被拦下（客户端永远看不到命中字面）
 * 2. 跨包词能被识别（SSE 分包切断不是漏检理由）
 * 3. 已下发部分 = 可回放的截断点（重生成的输入），offset 为全章坐标
 * 4. 无命中时正文完整（尾巴由 flush 补齐，不丢字）
 *
 * @author HL
 */
class StreamGuardTest {

    private final SensitiveTerms terms = new SensitiveTerms();

    /** 收集下发内容的辅助器 */
    private static final class Sink implements Consumer<String> {
        private final StringBuilder text = new StringBuilder();
        private final List<String> chunks = new ArrayList<>();

        @Override
        public void accept(String chunk) {
            chunks.add(chunk);
            text.append(chunk);
        }

        String text() {
            return text.toString();
        }
    }

    @Test
    @DisplayName("干净正文：flush 后完整下发，不丢字")
    void cleanTextIsForwardedInFullAfterFlush() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);

        out.accept("这是一段干净的正文");
        guard.flush(sink);

        assertEquals("这是一段干净的正文", sink.text());
        assertFalse(guard.tripped());
        assertEquals(sink.text(), guard.forwardedText());
    }

    @Test
    @DisplayName("尾巴按住不发：留足最长词窗口，flush 才补发")
    void tailIsHeldBackUntilFlush() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);
        String text = "这是一段干净的正文";

        out.accept(text);

        int window = terms.maxTermLength() - 1;
        assertEquals(text.length() - window, sink.text().length(), "应只下发到倒数第 window 个字符");
        assertTrue(text.startsWith(sink.text()));

        guard.flush(sink);
        assertEquals(text, sink.text());
    }

    @Test
    @DisplayName("跨包切开的命中词：第二包到达时整体识别并在下发前拦下")
    void termSplitAcrossChunksIsBlockedBeforeForwarding() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);

        out.accept("他掏出制");

        StreamSensitiveHitException hit = assertThrows(StreamSensitiveHitException.class,
                () -> out.accept("毒工艺的配方"));

        assertEquals("制毒工艺", hit.hits().get(0).term());
        assertEquals("", sink.text(), "命中前的短内容仍在窗口内，不应有泄漏");
        assertEquals("", hit.partialText());
        assertTrue(guard.tripped());
    }

    @Test
    @DisplayName("已下发部分作为截断点回传，命中 offset 为全章坐标")
    void alreadyForwardedPartIsReportedAsTruncationPoint() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);
        String clean = "夜色很沉，他慢慢翻开了那本旧账册。";

        out.accept(clean);
        StreamSensitiveHitException hit = assertThrows(StreamSensitiveHitException.class,
                () -> out.accept("制毒工艺"));

        assertEquals(clean.length() - (terms.maxTermLength() - 1), hit.partialText().length());
        assertEquals(hit.partialText(), sink.text(), "已下发内容必须与截断点一致");
        assertTrue(clean.contains(hit.partialText()));
        assertEquals(clean.length(), hit.hits().get(0).offset(), "offset 应为全章坐标（命中词在全章中的起点）");
    }

    @Test
    @DisplayName("命中词整体落在窗口内：accept 时即拦下，一个字符都不下发")
    void termInTailIsBlockedOnAccept() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);

        assertThrows(StreamSensitiveHitException.class, () -> out.accept("制毒工艺"));
        assertEquals("", sink.text(), "命中即断：尾部窗口内容同样不得下发");
        assertTrue(guard.tripped());
    }

    @Test
    @DisplayName("词表匹配大小写不敏感（英文词不因大小写漏检）")
    void hitsAreCaseInsensitive() {
        SensitiveTerms custom = new SensitiveTerms();
        custom.setCategories(Map.of("CUSTOM", List.of("BadWord")));
        StreamGuard guard = new StreamGuard(custom);
        Consumer<String> out = guard.guarded(new Sink());

        StreamSensitiveHitException hit = assertThrows(StreamSensitiveHitException.class,
                () -> out.accept("他低声说badword"));

        assertEquals("BadWord", hit.hits().get(0).term());
    }

    @Test
    @DisplayName("触发后不可复用：后续增量一律拒绝下发（异常被上层吞掉也不泄流）")
    void guardIsTerminalAfterTrip() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);

        assertThrows(StreamSensitiveHitException.class, () -> out.accept("制毒工艺"));
        assertThrows(StreamSensitiveHitException.class, () -> out.accept("干净的后续内容"));
        // 触发后 flush 是安全空操作：不留尾巴、也不下发任何内容
        guard.flush(sink);
        assertEquals("", sink.text());
    }

    @Test
    @DisplayName("空增量忽略，不影响窗口")
    void emptyChunksAreIgnored() {
        StreamGuard guard = new StreamGuard(terms);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);

        out.accept(null);
        out.accept("");
        out.accept("干净的正文");
        guard.flush(sink);

        assertEquals("干净的正文", sink.text());
    }

    @Test
    @DisplayName("无词表：全文直通（词表为空即无命中，等价现状行为）")
    void noTermsForwardsEverything() {
        StreamGuard guard = new StreamGuard(null);
        Sink sink = new Sink();
        Consumer<String> out = guard.guarded(sink);

        out.accept("制毒工艺");
        guard.flush(sink);

        assertEquals("制毒工艺", sink.text());
        assertFalse(guard.tripped());
    }
}
