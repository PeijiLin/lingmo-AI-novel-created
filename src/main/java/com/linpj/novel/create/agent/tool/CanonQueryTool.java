package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelEventTimeline;
import com.linpj.novel.create.pojo.entity.NovelStateFact;
import com.linpj.novel.create.utils.JsonUtil;
import com.linpj.novel.create.worldstate.EventTimelineService;
import com.linpj.novel.create.worldstate.StateHistoryService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 正典查询工具（自主性改造 R1a/R1b：§12.8——worldstate 只读包装）
 *
 * <p>模型通过 function calling 主动取证：角色当前态（solveLatest 帐）、事实切片
 * （solveState 按章号解算）、事件时间轴（timelineOf 全量限条）。
 * 只读——正典写通道不存在（读范围≠写授权）。
 *
 * <p>参数契约（inputSchema）：type=CHARACTER|FACT|TIMELINE；CHARACTER/FACT 必传
 * characterId；FACT 可传 toChapter（缺省=最新态）；TIMELINE 可传 limit（缺省 20）。
 *
 * <p>引文协议（R1b）：返回结构化条目 List&lt;Map&gt;（每条一项证据）——Registry 注入
 * evidenceId 后喂模型；错误提示仍为纯文本（错误不是证据，不参与引文）。
 *
 * @author HL
 */
@Component
public class CanonQueryTool implements AgentTool {

    public static final String NAME = "canon.query";

    private final StateHistoryService stateHistoryService;
    private final EventTimelineService eventTimelineService;

    public CanonQueryTool(StateHistoryService stateHistoryService,
                          EventTimelineService eventTimelineService) {
        this.stateHistoryService = stateHistoryService;
        this.eventTimelineService = eventTimelineService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询小说正典事实：CHARACTER=角色当前状态（characterId 必填）；"
                + "FACT=角色在某章号时的状态切片（characterId 必填，toChapter 可选=最新）；"
                + "TIMELINE=故事事件时间轴（按时间顺序，limit 可选）。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                  "type":{"type":"string","enum":["CHARACTER","FACT","TIMELINE"]},
                  "characterId":{"type":"integer","description":"角色 ID（CHARACTER/FACT 必填）"},
                  "toChapter":{"type":"integer","description":"截至章号（仅 FACT）"},
                  "limit":{"type":"integer","description":"返回条数上限（仅 TIMELINE，默认 20）"}
                },"required":["type"]}""";
    }

    /** 执行：证据型工具——返回结构化条目，由 Registry 注入 evidenceId（引文协议 R1b） */
    @Override
    public Object execute(AgentToolContext context, String argumentsJson) throws Exception {
        AgentTool.AgentContext ctx = context.toAgentContext();
        Map<String, Object> args = parseArgs(argumentsJson);
        String type = String.valueOf(args.getOrDefault("type", ""));
        Long bookId = ctx.bookId();
        if (bookId == null) {
            return "查询失败：本书上下文缺失（bookId 不可用）";
        }
        switch (type) {
            case "CHARACTER", "FACT" -> {
                Long characterId = asLong(args.get("characterId"));
                if (characterId == null) {
                    return "查询失败：characterId 必填（" + type + " 查询）";
                }
                NovelStateFact fact = "FACT".equals(type) && asLong(args.get("toChapter")) != null
                        ? stateHistoryService.solveState(bookId, characterId, asLong(args.get("toChapter")))
                        : stateHistoryService.solveLatest(bookId, characterId);
                if (fact == null) {
                    return "未查到角色状态：characterId=" + characterId;
                }
                return List.of(Map.of("fact", fact));
            }
            case "TIMELINE" -> {
                int limit = asInt(args.get("limit"), 20);
                List<NovelEventTimeline> timeline = eventTimelineService.timelineOf(bookId);
                if (timeline == null || timeline.isEmpty()) {
                    return "本书暂无事件时间轴记录";
                }
                return timeline.stream()
                        .limit(Math.max(1, limit))
                        .<Map<String, Object>>map(e -> Map.of("event", e))
                        .toList();
            }
            default -> {
                return "查询失败：未知类型 " + type + "（支持 CHARACTER/FACT/TIMELINE）";
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JsonUtil.fromJson(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue()
                : value instanceof String s && !s.isBlank() ? Long.parseLong(s.trim()) : null;
    }

    private static int asInt(Object value, int fallback) {
        Long parsed = asLong(value);
        return parsed == null ? fallback : Math.toIntExact(parsed);
    }
}
