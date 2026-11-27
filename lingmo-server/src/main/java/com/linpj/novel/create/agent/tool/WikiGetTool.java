package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelWikiAnchor;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiPageService;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 正典页查询工具（M4：实体定位入口——检索路由 §6.6 的工具面）
 *
 * <p>模型通过 function calling 主动取证：按别名定位正典页（纯 SQL，不走相似度），
 * 返回页卡（canonical + 硬 attrs）+ 别名 + 依据锚点。只读——正典写通道不存在。
 *
 * <p>引文协议（R1b 同款）：返回结构化条目，Registry 落回执后注入 evidenceId 喂模型；
 * Writer/Critic 引用页卡事实时绑定 evidenceId，引文有效率可回验。
 *
 * @author HL
 */
@Component
public class WikiGetTool implements AgentTool {

    public static final String NAME = "wiki.get";

    private final WikiPageService wikiPageService;

    public WikiGetTool(WikiPageService wikiPageService) {
        this.wikiPageService = wikiPageService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "查询本书正典设定页：传 name 按别名/角色名定位（推荐），或传 pageId 直取。"
                + "返回页卡正文、硬事实 attrs、别名与依据锚点。设定冲突以此为准。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                  "name":{"type":"string","description":"实体名/别名（与 pageId 二选一）"},
                  "pageId":{"type":"integer","description":"正典页 ID（与 name 二选一）"}
                }}""";
    }

    @Override
    public Object execute(AgentToolContext context, String argumentsJson) throws Exception {
        AgentTool.AgentContext ctx = context.toAgentContext();
        Map<String, Object> args = parseArgs(argumentsJson);
        Long bookId = ctx.bookId();
        if (bookId == null) {
            return "查询失败：本书上下文缺失（bookId 不可用）";
        }
        NovelWikiPage page = resolvePage(bookId, args);
        if (page == null) {
            return "未查到正典页（本书无此实体或别名）："
                    + (args.get("name") != null ? args.get("name") : args.get("pageId"));
        }
        // 证据型条目：Registry 注入 evidenceId（引文协议）
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("pageId", page.getId());
        entry.put("entityType", page.getEntityType());
        entry.put("title", page.getTitle());
        entry.put("canonical", page.getCanonical());
        if (page.getAttrs() != null) {
            entry.put("attrs", page.getAttrs());
        }
        entry.put("aliases", wikiPageService.aliasesOf(page.getId()));
        List<NovelWikiAnchor> anchors = wikiPageService.anchorsOf(page.getId());
        if (anchors != null && !anchors.isEmpty()) {
            entry.put("anchors", anchors.stream().map(anchor -> Map.of(
                    "field", anchor.getField() == null ? "CANON" : anchor.getField(),
                    "sourceType", anchor.getSourceType() == null ? "AUTHOR" : anchor.getSourceType(),
                    "chapterNum", anchor.getChapterNum() == null ? 0 : anchor.getChapterNum(),
                    "quote", anchor.getQuote() == null ? "" : anchor.getQuote()
            )).toList());
        }
        return List.of(entry);
    }

    private NovelWikiPage resolvePage(Long bookId, Map<String, Object> args) {
        Long pageId = asLong(args.get("pageId"));
        if (pageId != null) {
            try {
                return wikiPageService.getPage(pageId);
            } catch (Exception e) {
                return null; // 页不存在：作为"未查到"回喂（错误不是证据）
            }
        }
        Object name = args.get("name");
        if (name != null && !String.valueOf(name).isBlank()) {
            return wikiPageService.locateByAlias(bookId, String.valueOf(name));
        }
        return null;
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
}
