package com.linpj.novel.create.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容词表单元测试（M4-E3/E2 共用判定数据）
 *
 * 验收点：
 * - 基线词表在配置为空时仍然生效（安全层不可让渡：配置只能收紧不能放宽到 0）
 * - 配置追加：同分类追加、新分类可加
 * - 命中可解释：词、分类、位置齐全，且同位置长词优先
 * - 大小写不敏感（英文词不因大小写漏检）
 *
 * @author HL
 */
class SensitiveTermsTest {

    private SensitiveTerms terms(Map<String, List<String>> extra) {
        SensitiveTerms terms = new SensitiveTerms();
        terms.setCategories(extra);
        return terms;
    }

    @Test
    @DisplayName("基线词表：无任何配置时仍能命中（配置可追加，不可清空）")
    void baselineAppliesWithoutConfiguration() {
        List<SensitiveTerms.Hit> hits = terms(Map.of()).scan("他被开膛破肚了");

        assertEquals(1, hits.size());
        assertEquals("开膛破肚", hits.get(0).term());
        assertEquals("VIOLENCE", hits.get(0).category());
        assertEquals(2, hits.get(0).offset());
    }

    @Test
    @DisplayName("配置追加：新分类词表可命中，基线同时保留")
    void configurationAppendsToBaseline() {
        SensitiveTerms terms = terms(Map.of("custom", List.of("禁词甲")));

        List<SensitiveTerms.Hit> hits = terms.scan("禁词甲与肢解");

        assertEquals(2, hits.size());
        assertEquals("禁词甲", hits.get(0).term());
        assertEquals("CUSTOM", hits.get(0).category(), "配置分类名统一大写出账");
        assertEquals("肢解", hits.get(1).term());
    }

    @Test
    @DisplayName("同词重复配置不产生重复命中（先出现分类为准）")
    void duplicateTermCountedOnce() {
        List<SensitiveTerms.Hit> hits = terms(Map.of("VIOLENCE", List.of("肢解"))).scan("肢解");

        assertEquals(1, hits.size());
        assertEquals("VIOLENCE", hits.get(0).category());
    }

    @Test
    @DisplayName("大小写不敏感：英文配置词大小写混排也能命中")
    void matchingIsCaseInsensitive() {
        List<SensitiveTerms.Hit> hits = terms(Map.of("ILLEGAL", List.of("drug lab"))).scan("a DRUG LAB here");

        assertEquals(1, hits.size());
        assertEquals(2, hits.get(0).offset());
    }

    @Test
    @DisplayName("空值/正常文本：不误报")
    void benignTextProducesNoHit() {
        SensitiveTerms terms = terms(Map.of());

        assertTrue(terms.scan(null).isEmpty());
        assertTrue(terms.scan("").isEmpty());
        assertTrue(terms.scan("夜色沉沉，城墙之外是连绵的营火。").isEmpty());
    }

    @Test
    @DisplayName("长词优先 + 最长词长度可查（流式增量窗口下界）")
    void longestTermDrivesStreamWindow() {
        SensitiveTerms terms = terms(Map.of());

        assertEquals(6, terms.maxTermLength(), "基线最长词为「颠覆国家政权」");
        List<SensitiveTerms.Hit> hits = terms.scan("颠覆国家政权");
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).offset());
    }
}
