package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.rule.RuleCheckContext;
import com.linpj.novel.create.rule.RuleCheckResult;
import com.linpj.novel.create.rule.RuleChecker;
import com.linpj.novel.create.rule.RuleStage;
import com.linpj.novel.create.rule.RuleViolation;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则校验工具（自主性改造 R2b：§2——RuleChecker 当工具，确定性、零 LLM）
 *
 * <p>Planner L2 用大纲草案验可行性、Critic L2 可对正文跑规则——工具输出是
 * 结构化条目（List&lt;Map&gt;）：每条 violation 一个 evidenceId（违规即证据，
 * Planner 收口引文可直接绑定规则命中）。
 *
 * <p>参数：{outline: 大纲 JSON（OUTLINE 阶段）/ text: 正文（可选阶段），
 * targetWordCount?, pov?}——stage 由调用语境固定 OUTLINE（Planner 白名单语义）。
 *
 * @author HL
 */
@Component
public class RuleCheckTool implements AgentTool {

    public static final String NAME = "rulecheck.run";

    private final RuleChecker ruleChecker;

    public RuleCheckTool(RuleChecker ruleChecker) {
        this.ruleChecker = ruleChecker;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "对大纲草案执行确定性规则校验（结构完整性/参与角色/伏笔排期等，零模型参与）。"
                + "返回违规清单（每条带 evidenceId）；blocking 违规必须修订后收口。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                  "outline":{"type":"object","description":"待验大纲 JSON（chapterTitle + scenes 数组）"},
                  "targetWordCount":{"type":"integer"},
                  "pov":{"type":"string"}
                },"required":["outline"]}""";
    }

    /** 执行：规则校验（确定性）；返回结构化条目（每条违规一证据） */
    @Override
    public Object execute(AgentToolContext context, String argumentsJson) {
        try {
            Map<String, Object> args = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of() : JsonUtil.fromJson(argumentsJson, Map.class);
            Object outlineRaw = args.get("outline");
            if (outlineRaw == null) {
                return "校验失败：outline 必填";
            }
            ChapterOutline outline = JsonUtil.fromJson(JsonUtil.toJson(outlineRaw), ChapterOutline.class);
            RuleCheckContext ruleContext = RuleCheckContext.of(RuleStage.OUTLINE);
            ruleContext.setOutline(outline);
            Object wordCount = args.get("targetWordCount");
            if (wordCount instanceof Number n) {
                ruleContext.setTargetWordCount(n.intValue());
            }
            Object pov = args.get("pov");
            if (pov instanceof String s && !s.isBlank()) {
                ruleContext.setPov(s);
            }
            RuleCheckResult result = ruleChecker.check(ruleContext);
            List<RuleViolation> violations = result.getBlocking();
            if (violations == null || violations.isEmpty()) {
                // 空结果给一条"通过"占位（收口可绑定规则通过的事实；非结构化文本不产生证据）
                return List.of(Map.of("passed", true, "executedRules", result.getExecutedRules().size()));
            }
            return violations.stream()
                    .<Map<String, Object>>map(v -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("ruleId", v.getRuleId());
                        entry.put("violation", v.getEvidence() == null
                                ? (v.getSuggestion() == null ? String.valueOf(v) : v.getSuggestion())
                                : v.getEvidence());
                        entry.put("blocking", true);
                        return entry;
                    })
                    .toList();
        } catch (Exception e) {
            return "校验失败：" + e.getMessage();
        }
    }
}
