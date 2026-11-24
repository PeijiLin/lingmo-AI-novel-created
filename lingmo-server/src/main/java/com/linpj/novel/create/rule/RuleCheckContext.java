package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 规则校验上下文（编排器在 N3/N5 调用前装配；字段按需扩展，规则自行做空值容忍）
 *
 * 信息最小可见（设计公理 4 的规则侧贯彻）：规则拿到的是判定所需的最小事实集，
 * 不透传整书内容与 meta 信息。
 *
 * @author HL
 */
@Data
public class RuleCheckContext implements Serializable {

    /** 所属书籍（规则只读查询角色卡/状态表等账本数据用） */
    private Long bookId;

    /** 校验时机 */
    private RuleStage stage;

    /** 成稿正文（N5；N3 为空） */
    private String draft;

    /** 章节大纲（N3/N5 均可空 = Planner 降级直写场景） */
    private ChapterOutline outline;

    /** 目标字数（可空 = 未配置，字数规则跳过） */
    private Integer targetWordCount;

    /** 视角约束（如"第三人称限知"；可空 = 未配置，POV 规则跳过） */
    private String pov;

    /** 本书豁免的规则 ID（作者误报标记产物，D6 误报闭环写入；豁免即不检） */
    private Set<String> exemptedRules = new LinkedHashSet<>();

    /** 当前章节（v2.0 M4-E5：区间保护/叙诡保护区按章判定；N3 可空 = 未知章节不做区间保护） */
    private Long chapterId;

    /** 区间内受保护的规则 ID（E5：命中只落 shadow 不阻塞，区间外照常拦截） */
    private Set<String> protectedRules = new LinkedHashSet<>();

    /** 本章是否处于叙诡保护区（大纲存在作者显式声明的 UNRELIABLE 场景） */
    private boolean unreliableZone;

    public static RuleCheckContext of(RuleStage stage) {
        RuleCheckContext context = new RuleCheckContext();
        context.setStage(stage);
        return context;
    }
}
