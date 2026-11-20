package com.linpj.novel.create.rule;

/**
 * 校验时机（设计 5.1 DAG：N3 细纲预审 / N5 成稿终审）
 *
 * @author HL
 */
public enum RuleStage {
    /** N3：细纲预审（Planner 产物） */
    OUTLINE,
    /** N5：成稿终审（Writer 成稿） */
    DRAFT
}
