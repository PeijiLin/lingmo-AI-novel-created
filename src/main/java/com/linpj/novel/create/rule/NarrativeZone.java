package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.NarrativeMode;
import com.linpj.novel.create.pojo.pipeline.Scene;

/**
 * 叙诡保护区判定（v2.0 M4-E5）
 *
 * 保护区只由**大纲中显式标注**的 UNRELIABLE 场景进入（设计评审 N2：叙诡必须作者声明，
 * 不能由模型自行"解释"成不可靠叙述），因此模型自由发挥的正文不会被自动豁免。
 * 判定放在"章"这一粒度：只要本章存在任一 UNRELIABLE 场景，本章的一致性违规即按保护区
 * 处理（只告警）——不可靠叙述下"事实矛盾"可能就是作者故意藏的线索。
 *
 * @author HL
 */
public final class NarrativeZone {

    private NarrativeZone() {
    }

    /** 本章是否处于叙诡保护区 */
    public static boolean declaresUnreliable(ChapterOutline outline) {
        if (outline == null || outline.getScenes() == null) {
            return false;
        }
        return outline.getScenes().stream()
                .anyMatch(scene -> scene != null && NarrativeMode.UNRELIABLE == scene.getNarrativeMode());
    }

    /** 保护区来源说明（日志/产物留证用） */
    public static String describe(ChapterOutline outline) {
        if (!declaresUnreliable(outline)) {
            return "";
        }
        int unreliableScenes = 0;
        for (Scene scene : outline.getScenes()) {
            if (scene != null && NarrativeMode.UNRELIABLE == scene.getNarrativeMode()) {
                unreliableScenes++;
            }
        }
        return "UNRELIABLE:" + unreliableScenes + "/" + outline.getScenes().size();
    }
}
