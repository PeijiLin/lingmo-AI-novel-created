package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.NarrativeMode;
import com.linpj.novel.create.pojo.pipeline.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 叙诡保护区判定单元测试（v2.0 M4-E5：只认作者显式声明）
 *
 * @author HL
 */
class NarrativeZoneTest {

    private static Scene scene(NarrativeMode mode) {
        Scene scene = new Scene();
        scene.setNarrativeMode(mode);
        return scene;
    }

    private static ChapterOutline outlineOf(Scene... scenes) {
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(new ArrayList<>(List.of(scenes)));
        return outline;
    }

    @Test
    void 空大纲不进入保护区() {
        assertFalse(NarrativeZone.declaresUnreliable(null));
        assertFalse(NarrativeZone.declaresUnreliable(new ChapterOutline()));
        assertFalse(NarrativeZone.declaresUnreliable(outlineOf()));
        assertEquals("", NarrativeZone.describe(null));
    }

    @Test
    void 常规与倒叙不进入保护区() {
        ChapterOutline outline = outlineOf(scene(NarrativeMode.NORMAL), scene(NarrativeMode.FLASHBACK));

        assertFalse(NarrativeZone.declaresUnreliable(outline));
        assertEquals("", NarrativeZone.describe(outline));
    }

    @Test
    void 显式声明UNRELIABLE才进入保护区() {
        ChapterOutline outline = outlineOf(scene(NarrativeMode.NORMAL), scene(NarrativeMode.UNRELIABLE));

        assertTrue(NarrativeZone.declaresUnreliable(outline));
        assertEquals("UNRELIABLE:1/2", NarrativeZone.describe(outline));
    }

    @Test
    void 场景缺省模式按常规处理_空场景元素不炸() {
        Scene noMode = new Scene();
        Scene nullScene = null;
        ChapterOutline outline = new ChapterOutline();
        outline.setScenes(new ArrayList<>(List.of(noMode)));
        assertFalse(NarrativeZone.declaresUnreliable(outline));

        outline.setScenes(new ArrayList<>(java.util.Arrays.asList(nullScene, scene(NarrativeMode.UNRELIABLE))));
        assertTrue(NarrativeZone.declaresUnreliable(outline), "空场景元素应被跳过而非抛错");
        assertEquals("UNRELIABLE:1/2", NarrativeZone.describe(outline));
    }
}
