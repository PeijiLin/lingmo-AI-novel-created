package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.SummaryTreeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 分层摘要查询工具（自主性改造 R1a：§12.8——summaryTree/滚动摘要只读包装）
 *
 * <p>level=CHAPTER（指定章号区间）/ SEGMENT（全部分段中摘）/ BOOK（全书短摘）。
 * 摘要树未生成（开关关闭或未落节点）时返回提示文本——空结果是合法事实（不伪造）。
 *
 * @author HL
 */
@Component
public class SummaryGetTool implements AgentTool {

    public static final String NAME = "summary.get";

    private final SummaryTreeService summaryTreeService;

    public SummaryGetTool(SummaryTreeService summaryTreeService) {
        this.summaryTreeService = summaryTreeService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询分层前情摘要：CHAPTER=章级详摘（fromChapter/toChapter 指定区间，缺省最近 10 章）；"
                + "SEGMENT=分段中摘（每 10 章一节点）；BOOK=全书短摘。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                  "level":{"type":"string","enum":["CHAPTER","SEGMENT","BOOK"]},
                  "fromChapter":{"type":"integer","description":"起始章号（仅 CHAPTER）"},
                  "toChapter":{"type":"integer","description":"截止章号（仅 CHAPTER）"}
                },"required":["level"]}""";
    }

    @Override
    public String execute(AgentToolContext context, String argumentsJson) throws Exception {
        AgentTool.AgentContext ctx = context.toAgentContext();
        Long bookId = ctx.bookId();
        if (bookId == null) {
            return "查询失败：本书上下文缺失（bookId 不可用）";
        }
        Map<String, Object> args = parseArgs(argumentsJson);
        String level = String.valueOf(args.getOrDefault("level", "")).toUpperCase();
        switch (level) {
            case "CHAPTER" -> {
                Integer from = asInteger(args.get("fromChapter"));
                Integer to = asInteger(args.get("toChapter"));
                if (from == null) {
                    to = to == null ? null : to;
                    from = to == null ? 1 : Math.max(1, to - 9);
                }
                int end = to == null ? from + 9 : to;
                List<String> digests = summaryTreeService.loadChapterDigests(bookId, from, end);
                if (digests == null || digests.isEmpty()) {
                    return "章级摘要未生成（from=" + from + ",to=" + end + "）——可改查 SEGMENT 或 BOOK";
                }
                return JsonUtil.toJson(Map.of("level", "CHAPTER", "from", from, "to", end, "digests", digests));
            }
            case "SEGMENT" -> {
                List<String> digests = summaryTreeService.loadSegmentDigests(bookId);
                if (digests == null || digests.isEmpty()) {
                    return "分段摘要未生成——可改查 BOOK";
                }
                return JsonUtil.toJson(Map.of("level", "SEGMENT", "digests", digests));
            }
            case "BOOK" -> {
                Long chapterId = ctx.chapterId();
                String digest = chapterId == null
                        ? summaryTreeService.nodeContent(bookId, "BOOK", 0)
                        : summaryTreeService.renderTreeDigest(bookId, chapterId);
                if (digest == null || digest.isBlank()) {
                    return "全书摘要未生成";
                }
                return JsonUtil.toJson(Map.of("level", "BOOK", "digest", digest));
            }
            default -> {
                return "查询失败：未知层级 " + level + "（支持 CHAPTER/SEGMENT/BOOK）";
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtil.fromJson(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
