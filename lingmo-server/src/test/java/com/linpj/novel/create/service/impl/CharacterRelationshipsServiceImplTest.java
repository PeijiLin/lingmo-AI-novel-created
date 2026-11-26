package com.linpj.novel.create.service.impl;

import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关系边生效区间判定（与 RC-08 / state_fact 同语义：闭区间、null 为开边界）
 *
 * @author HL
 */
class CharacterRelationshipsServiceImplTest {

    private CharacterRelationships edge(Integer from, Integer to) {
        CharacterRelationships edge = new CharacterRelationships();
        edge.setFromCharacterId(1L);
        edge.setToCharacterId(2L);
        edge.setRelationshipType("师徒");
        edge.setChapterFrom(from);
        edge.setChapterTo(to);
        return edge;
    }

    @Test
    void longTermEdgeActiveWithoutChapterNumber() {
        assertTrue(CharacterRelationshipsServiceImpl.activeAt(edge(null, null), null));
    }

    @Test
    void intervalEdgeInactiveWhenChapterUnknown() {
        assertFalse(CharacterRelationshipsServiceImpl.activeAt(edge(3, 9), null));
    }

    @Test
    void intervalEdgeActiveInsideClosedRange() {
        assertTrue(CharacterRelationshipsServiceImpl.activeAt(edge(3, 9), 3));
        assertTrue(CharacterRelationshipsServiceImpl.activeAt(edge(3, 9), 6));
        assertTrue(CharacterRelationshipsServiceImpl.activeAt(edge(3, 9), 9));
    }

    @Test
    void intervalEdgeInactiveOutsideRange() {
        assertFalse(CharacterRelationshipsServiceImpl.activeAt(edge(3, 9), 2));
        assertFalse(CharacterRelationshipsServiceImpl.activeAt(edge(3, 9), 10));
    }

    @Test
    void openBoundarySemantics() {
        assertTrue(CharacterRelationshipsServiceImpl.activeAt(edge(5, null), 6));
        assertFalse(CharacterRelationshipsServiceImpl.activeAt(edge(5, null), 4));
        assertTrue(CharacterRelationshipsServiceImpl.activeAt(edge(null, 5), 4));
        assertFalse(CharacterRelationshipsServiceImpl.activeAt(edge(null, 5), 6));
    }
}
