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

    /** 结构校验结果 */
    record ContractCheck(boolean ok, String violations) {

        public static ContractCheck passed() {
            return new ContractCheck(true, null);
        }

        public static ContractCheck fail(String violations) {
            return new ContractCheck(false, violations == null ? "校验未通过" : violations);
        }
    }
}
