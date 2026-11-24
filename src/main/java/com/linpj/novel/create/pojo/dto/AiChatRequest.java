package com.linpj.novel.create.pojo.dto;

import com.linpj.novel.create.pojo.pipeline.CreationMode;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * @author HL
 */
@Data
public class AiChatRequest implements Serializable {
    Long bookId;
    String userInput;
    Long chapterId;
    String creationType;
    String title;
    String worldSetting;
    String style;

    /**
     * 创作模式：FAST（默认，现有单链路）/ STANDARD / REFINED / PRECISE。
     * 老前端不传该字段即 FAST，五个现有端点行为不变。
     */
    CreationMode mode;

    /**
     * Critic 重写轮数上限（默认 2，硬上限 3），仅 STANDARD/REFINED/PRECISE 生效。
     */
    Integer maxRewriteRounds;

    /** 本章目标字数（可空：未配置时 RC-11 字数规则静默） */
    Integer targetWordCount;

    /** 视角约束（如"第三人称限知"；可空：未配置时 RC-12 POV 规则静默） */
    String pov;

    /**
     * 机制开关请求级覆盖（开关式双跑，v2.0 设计 7.1）：
     * 键为 PipelineFeatures 已知开关名（summaryTree/triTimeline/credibility/ruleRouting/convergenceGuard），
     * 未知键服务端忽略；null/缺省 = 用部署默认值。实验时一次只覆盖一个开关。
     */
    Map<String, Boolean> features;

    /**
     * 自主性等级请求级覆盖（自主性改造 R2b，L4 定制档）：
     * 键为节点名（PLANNER/WRITER/CRITIC），值 "L1"/"L2"（大小写宽容）；
     * 未知键/未知值服务端忽略；null/缺省 = 用模式矩阵默认。优先级：请求覆盖 > 矩阵 > L1 兜底。
     */
    Map<String, String> autonomyOverrides;
}
