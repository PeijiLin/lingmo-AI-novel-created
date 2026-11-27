package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelWikiAnchor;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * wiki.get 工具单元测试（M4：实体定位入口——检索路由的工具面）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
class WikiGetToolTest {

    @Mock private WikiPageService wikiPageService;

    private WikiGetTool tool;
    private AgentToolContext context;

    @BeforeEach
    void setUp() {
        tool = new WikiGetTool(wikiPageService);
        context = AgentToolContext.of(7L, "WRITING:1", Map.of("bookId", 1L, "chapterId", 10L));
    }

    private NovelWikiPage page() {
        NovelWikiPage page = new NovelWikiPage();
        page.setId(33L);
        page.setBookId(1L);
        page.setEntityType("CHARACTER");
        page.setEntityKey("char:1");
        page.setTitle("林澈");
        page.setCanonical("三清山外门弟子，性沉静。");
        page.setAttrs(Map.of("gender", "男", "age", 19));
        return page;
    }

    @Test
    void locateByNameReturnsEvidenceEntry() throws Exception {
        when(wikiPageService.locateByAlias(1L, "林澈")).thenReturn(page());
        when(wikiPageService.aliasesOf(33L)).thenReturn(List.of("林澈", "小林"));
        when(wikiPageService.anchorsOf(33L)).thenReturn(List.of());

        Object result = tool.execute(context, "{\"name\":\"林澈\"}");

        // 证据型返回：List<Map>（Registry 注入 evidenceId）
        assertInstanceOf(List.class, result);
        List<?> entries = (List<?>) result;
        assertEquals(1, entries.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) entries.get(0);
        assertEquals(33L, entry.get("pageId"));
        assertEquals("林澈", entry.get("title"));
        assertEquals(Map.of("gender", "男", "age", 19), entry.get("attrs"));
        assertEquals(List.of("林澈", "小林"), entry.get("aliases"));
    }

    @Test
    void pageIdLookupWithAnchors() throws Exception {
        when(wikiPageService.getPage(33L)).thenReturn(page());
        when(wikiPageService.aliasesOf(33L)).thenReturn(List.of("林澈"));
        NovelWikiAnchor anchor = new NovelWikiAnchor();
        anchor.setPageId(33L);
        anchor.setField("gender");
        anchor.setSourceType("AUTHOR");
        anchor.setQuote("作者录入");
        when(wikiPageService.anchorsOf(33L)).thenReturn(List.of(anchor));

        Object result = tool.execute(context, "{\"pageId\":33}");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) result;
        assertEquals(1, entries.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> anchors = (List<Map<String, Object>>) entries.get(0).get("anchors");
        assertEquals("gender", anchors.get(0).get("field"));
        assertEquals("AUTHOR", anchors.get(0).get("sourceType"));
    }

    @Test
    void missReturnsPlainMessageNotEvidence() throws Exception {
        when(wikiPageService.locateByAlias(1L, "不存在")).thenReturn(null);

        Object result = tool.execute(context, "{\"name\":\"不存在\"}");

        // 未命中是错误提示（纯文本），不是证据——不参与引文
        assertInstanceOf(String.class, result);
        assertTrue(String.valueOf(result).contains("未查到"));
    }

    @Test
    void missingBookIdReturnsPlainError() throws Exception {
        AgentToolContext noBook = AgentToolContext.of(7L, "WRITING:1", Map.of());

        Object result = tool.execute(noBook, "{\"name\":\"林澈\"}");

        assertInstanceOf(String.class, result);
        assertTrue(String.valueOf(result).contains("bookId"));
    }

    @Test
    void pageIdLookupOfMissingPageReturnsMissMessage() throws Exception {
        when(wikiPageService.getPage(99L))
                .thenThrow(new com.linpj.novel.create.exception.BusinessException(
                        com.linpj.novel.create.exception.ErrorCode.NOT_FOUND_ERROR));

        Object result = tool.execute(context, "{\"pageId\":99}");

        assertInstanceOf(String.class, result, "页不存在：错误提示（不是证据）");
    }
}
