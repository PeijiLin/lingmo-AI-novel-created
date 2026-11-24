package com.linpj.novel.create.agent.runtime;

/**
 * 收口契约（自主性改造 R1a：§12.2——"finish 必须过代码校验，不许假报成功"的契约面）
 *
 * <p>R1a 为结构校验（JSON 可解析 + 必填非空）；R1b 扩展引文存在性（evidenceId
 * 回执绑定）与提案合法性硬校验——接口不动，实现演进。
 *
 * <p>finishSchema / expectations 由节点提供，AgentProtocol 织入元工具描述与协议块
 * ——schema 是模型侧契约（输出什么），validate 是代码侧契约（收下什么），两侧同源。
 *
 * @author HL
 */
public interface DeliverableContract {

    /**
     * finish 元工具 deliverable 参数的 JSON Schema（织入 agent_finish 工具描述，
     * 模型按此结构产出）。
     */
    String finishSchema();

    /** 收口期望说明（织入协议块：交付什么、什么算合格） */
    String expectations();

    /**
     * 交付校验（代码侧，不许假报成功的物理实现）。
     *
     * @param deliverableJson finish 参数中的 deliverable 子树（字符串化 JSON）
     * @return 校验结果；不通过给 violations 作为修复步输入
     */
    ContractCheck validate(String deliverableJson);

    /**
     * 引文校验钩子（R1b 引文协议，§11.1-M1）：结构校验通过后由 Runtime 调用。
     *
     * <p>节点实现逐条校验 evidenceId（确定性存在性：回执存在+序号界内+文本一致）
     * 并落引文绑定账（ok 与否都留痕）；R2 Writer/Planner 按各自语义实现
     * （提案合法性等）。默认通过（L1a 阶段实现无需感知）。
     *
     * @param finishArgumentsJson finish 工具参数原文（含 deliverable/claims/unresolved 全树）
     * @param stepNo             收口步号（引文账本定位）
     */
    default CitationOutcome verifyCitations(String finishArgumentsJson, int stepNo) {
        return CitationOutcome.pass();
    }

    /** 结构校验结果 */
    record ContractCheck(boolean ok, String violations) {

        public static ContractCheck passed() {
            return new ContractCheck(true, null);
        }

        public static ContractCheck fail(String violations) {
            return new ContractCheck(false, violations == null ? "校验未通过" : violations);
        }
    }

    /** 引文校验结果（total/valid 供 E5 引文有效率指标与 SSE 事件） */
    record CitationOutcome(boolean ok, String violations, int total, int valid) {

        public static CitationOutcome pass() {
            return new CitationOutcome(true, null, 0, 0);
        }
    }
}
