package com.linpj.novel.create.rule;

import java.util.List;

/**
 * 确定性一致性规则契约（设计公理 3：判定确定化——能确定性判定的绝不交给 LLM）
 *
 * - 判定必须确定性/启发式可解释：同样输入产生同样违规清单；启发式规则须在 javadoc 诚实声明局限
 * - 写副作用禁止；只读查询（角色卡/人物状态等账本）允许——规则自行对数据缺失做空值容忍
 *   （返回空清单静默，不抛异常：数据通道故障时宁可漏检不可炸管道）
 * - 单条规则抛异常由 RuleChecker 兜底（降级为告警，不阻塞出章——规则自身故障
 *   不应放大道数为不可用，观察优于中断）
 *
 * @author HL
 */
public interface ConsistencyRule {

    /** 规则 ID（如 RC-01，对应设计 6.7 规则表） */
    String id();

    /** 适用校验时机（OUTLINE / DRAFT / 二者） */
    List<RuleStage> stages();

    /** 默认严重级（设计 6.7 规则表；个别违规可依据上下文自定） */
    RuleSeverity severity();

    /** 规则一句话说明（配置校验与审计可读性） */
    String description();

    /** 执行校验；无违规返回空清单 */
    List<RuleViolation> check(RuleCheckContext context);
}
