package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.CanonProposalService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 正典提案工具（自主性改造 R2a：§2——proposal.emit，只暂存不写入）
 *
 * <p>Writer L2 在起草中产生的状态变更事实（角色伤势/位置等）通过本工具暂存
 * （novel_canon_proposal，PENDING）；提交门在节点 SUCCESS 后统一裁定——
 * <b>读范围≠写授权</b>：本工具是唯一"写形"通道且只到暂存层，任何 Agent 无正典直写。
 *
 * <p>返回结构化条目（List&lt;Map&gt;）：每条提案带 proposalId——Writer finish 的
 * 交付汇报可回引（存哪了）。
 *
 * @author HL
 */
@Component
public class ProposalEmitTool implements AgentTool {

    public static final String NAME = "proposal.emit";

    /** 工具上下文键：步号（步账 receipt 关联用；由 Gateway 注入，缺省 null） */
    public static final String CTX_STEP_NO = "l2StepNo";

    private final CanonProposalService proposalService;

    public ProposalEmitTool(CanonProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "暂存正典提案（状态变更事实，如角色伤势/位置变化）。提案不会立即写入正典——"
                + "在本章节点成功后由提交门统一裁定。每条提案返回 proposalId 供交付汇报引用。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                  "proposals":{"type":"array","minItems":1,"items":{"type":"object","properties":{
                    "type":{"type":"string","enum":["STATE"],"description":"提案类型（当前仅状态事实）"},
                    "characterId":{"type":"integer","description":"角色 ID"},
                    "state":{"type":"object","description":"状态变更内容（如 {\\\"伤势\\\": \\\"左臂痊愈\\\"}）"},
                    "note":{"type":"string","description":"提案理由/出处说明"}
                  },"required":["type","characterId","state"]}}
                },"required":["proposals"]}""";
    }

    /** 执行：暂存提案（幂等），返回结构化条目（proposalId + 内容回显） */
    @Override
    public Object execute(AgentToolContext context, String argumentsJson) {
        Long runId = context.runId();
        String nodeId = context.nodeId();
        Object stepNoRaw = context.data() == null ? null : context.data().get(CTX_STEP_NO);
        Integer stepNo = stepNoRaw instanceof Number n ? n.intValue() : null;

        List<Map<String, Object>> proposals;
        try {
            Map<String, Object> args = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of() : JsonUtil.fromJson(argumentsJson, Map.class);
            Object raw = args.get("proposals");
            proposals = raw instanceof List<?> list
                    ? (List<Map<String, Object>>) (List<?>) list : List.of();
        } catch (Exception e) {
            return "提案失败：参数解析错误（" + e.getMessage() + "）";
        }
        if (proposals.isEmpty()) {
            return "提案失败：proposals 不能为空";
        }

        List<Map<String, Object>> stored = new ArrayList<>(proposals.size());
        for (Map<String, Object> proposal : proposals) {
            if (proposal == null) {
                continue;
            }
            String proposalJson = JsonUtil.toJson(proposal);
            Long id = proposalService.record(runId, nodeId, stepNo, null,
                    proposalJson, ToolRegistry.sha256(proposalJson));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("proposalId", id == null ? "dedup-or-fail" : ("P" + id));
            entry.put("proposal", proposal);
            stored.add(entry);
        }
        return stored;
    }
}
