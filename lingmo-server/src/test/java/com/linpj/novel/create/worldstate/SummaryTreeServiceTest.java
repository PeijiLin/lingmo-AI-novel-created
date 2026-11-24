package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.NovelSummaryTree;
import com.linpj.novel.create.service.BookChapterService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 摘要树服务测试（M5-B4：定长不变式 / 三层结构 / 渲染窗口 / 幂等 upsert）
 *
 * 桩策略（同项目 PlotThreadServiceImplTest 模式）：findNode 为内存仓接缝（包可见），
 * 渲染测试直接 stub nodeContent/loadChapterDigests 公共读取口——DB 侧 wrapper 过滤语义属集成域。
 *
 * @author HL
 */
class SummaryTreeServiceTest {

    private BookChapterService bookChapterService;
    private SummaryTreeService service;

    /** 内存节点仓（key: level|seq） */
    private final Map<String, NovelSummaryTree> store = new HashMap<>();

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelSummaryTree.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                BookChapter.class);
    }

    @BeforeEach
    void setUp() {
        bookChapterService = mock(BookChapterService.class);
        service = spy(new SummaryTreeService(bookChapterService));
        store.clear();
        // findNode：内存仓精确命中（写路径 upsert 与 nodeContent 的接缝）
        doAnswer(invocation -> {
            String level = invocation.getArgument(1);
            Integer seq = invocation.getArgument(2);
            return store.get(level + "|" + seq);
        }).when(service).findNode(any(), any(), any());
        doAnswer(invocation -> {
            NovelSummaryTree node = invocation.getArgument(0);
            node.setId((long) (store.size() + 1));
            store.put(node.getLevel() + "|" + node.getSeq(), node);
            return true;
        }).when(service).save(any(NovelSummaryTree.class));
        doAnswer(invocation -> {
            NovelSummaryTree node = invocation.getArgument(0);
            store.put(node.getLevel() + "|" + node.getSeq(), node);
            return true;
        }).when(service).updateById(any(NovelSummaryTree.class));
    }

    private void chapter(Long id, int num) {
        BookChapter chapter = new BookChapter();
        chapter.setId(id);
        chapter.setBookId(1L);
        chapter.setChapterNum(num);
        when(bookChapterService.getById(id)).thenReturn(chapter);
    }

    private void node(String level, int seq, String content) {
        NovelSummaryTree node = new NovelSummaryTree();
        node.setBookId(1L);
        node.setLevel(level);
        node.setSeq(seq);
        node.setContent(content);
        node.setIsStale(false);
        node.setVersion(1);
        store.put(level + "|" + seq, node);
    }

    // ------------------------------------------------------------------
    // 区段数学与截断（纯函数）
    // ------------------------------------------------------------------

    @Test
    void 区段序号映射() {
        assertEquals(0, SummaryTreeService.segmentOf(1), "章 1-10 -> 段 0");
        assertEquals(0, SummaryTreeService.segmentOf(10));
        assertEquals(1, SummaryTreeService.segmentOf(11), "章 11-20 -> 段 1");
        assertEquals(3, SummaryTreeService.segmentOf(35));
    }

    @Test
    void 定长截断() {
        assertEquals("五百字内", SummaryTreeService.truncate("五百字内", 100));
        assertEquals(300, SummaryTreeService.truncate("长".repeat(500), 300).length(),
                "超限硬截断——定长不变式的执行点");
        assertNull(SummaryTreeService.truncate(null, 100));
    }

    // ------------------------------------------------------------------
    // 章详摘入账（upsert 幂等 + 血缘 stale）
    // ------------------------------------------------------------------

    @Test
    void 章详摘入账返回章号() {
        chapter(10L, 14);

        Integer num = service.recordChapterDigest(1L, 10L, "第十四章详摘");

        assertEquals(14, num);
        NovelSummaryTree saved = store.get("CHAPTER|14");
        assertNotNull(saved);
        assertEquals("第十四章详摘", saved.getContent());
        assertEquals(Long.valueOf(10L), saved.getChapterId(), "CHAPTER 层留章节溯源");
    }

    @Test
    void 章详摘超限截断() {
        chapter(10L, 14);

        service.recordChapterDigest(1L, 10L, "详".repeat(800));

        assertEquals(500, store.get("CHAPTER|14").getContent().length(),
                "LLM 不听话也截到 500——定长靠代码不靠模型自觉");
    }

    @Test
    void 章号解析失败返回null不入账() {
        when(bookChapterService.getById(99L)).thenReturn(null);

        assertNull(service.recordChapterDigest(1L, 99L, "详摘"));
        assertNull(service.recordChapterDigest(1L, null, "详摘"));
        verify(service, never()).save(any(NovelSummaryTree.class));
    }

    @Test
    void 下层写入置上层血缘stale() {
        chapter(10L, 14);
        node("BOOK", 0, "全书短摘");
        node("SEGMENT", 1, "段一中摘");

        service.recordChapterDigest(1L, 10L, "第十四章详摘");

        assertTrue(store.get("BOOK|0").getIsStale(), "章写入 -> 书置 stale");
        assertTrue(store.get("SEGMENT|1").getIsStale(), "章写入 -> 所属段置 stale");
    }

    @Test
    void 段重算清除stale且版本递增() {
        node("SEGMENT", 1, "旧段摘");
        store.get("SEGMENT|1").setIsStale(true);

        service.upsertNode(1L, "SEGMENT", 1, "新段摘");

        NovelSummaryTree updated = store.get("SEGMENT|1");
        assertEquals("新段摘", updated.getContent());
        assertFalse(updated.getIsStale(), "重算清除血缘标记");
        assertEquals(2, updated.getVersion(), "重算版本 +1（幂等对账）");
    }

    @Test
    void 新节点首发版本为1() {
        service.upsertNode(1L, "BOOK", 0, "首个全书短摘");

        assertEquals(1, store.get("BOOK|0").getVersion());
    }

    @Test
    void 同章重算幂等_内容一致() {
        chapter(10L, 14);
        service.recordChapterDigest(1L, 10L, "第十四章详摘");
        service.recordChapterDigest(1L, 10L, "第十四章详摘");

        assertEquals("第十四章详摘", store.get("CHAPTER|14").getContent(), "重入结果一致（幂等重建）");
        assertEquals(2, store.get("CHAPTER|14").getVersion());
    }

    // ------------------------------------------------------------------
    // 渲染（H2 机制核心：定长不变式 + 三层拼装）——直接 stub 读取口
    // ------------------------------------------------------------------

    /** 渲染近章窗口桩 */
    private void stubRecentChapters(List<String> chapters) {
        doReturn(chapters).when(service).loadChapterDigests(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 渲染三层拼装完整形态() {
        chapter(50L, 35);
        doReturn("全书短摘").when(service).nodeContent(1L, "BOOK", 0);
        doReturn("段三中摘").when(service).nodeContent(1L, "SEGMENT", 3);
        stubRecentChapters(List.of("章三十三详摘", "章三十四详摘", "章三十五详摘"));

        String rendered = service.renderTreeDigest(1L, 50L);

        assertNotNull(rendered);
        assertTrue(rendered.contains("【全书概览】全书短摘"));
        assertTrue(rendered.contains("【前情脉络】段三中摘"));
        assertTrue(rendered.contains("【最近章节】章三十三详摘 章三十四详摘 章三十五详摘"),
                "近 3 章升序拼接");
    }

    @Test
    void 渲染窗口只取最近三章() {
        chapter(50L, 35);
        // 窗口语义由 loadChapterDigests(from=33, to=35) 的调用参数承载——捕获参数断言
        doReturn(List.of("章三十三", "章三十四", "章三十五")).when(service)
                .loadChapterDigests(any(), org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());

        service.renderTreeDigest(1L, 50L);

        // 窗口语义由调用参数承载：[current-2, current] = [33, 35]
        verify(service).loadChapterDigests(1L, 33, 35);
    }

    @Test
    void 首十章无段无书仍可渲染() {
        chapter(5L, 3);
        stubRecentChapters(List.of("第三章详摘"));

        String rendered = service.renderTreeDigest(1L, 5L);

        assertNotNull(rendered, "早期章节：段/书缺位时仅渲染章层（三层各自可缺）");
        assertTrue(rendered.contains("第三章详摘"));
        assertFalse(rendered.contains("【全书概览】"));
    }

    @Test
    void 当前段缺失回退上一段() {
        chapter(50L, 15);   // 段 1
        doReturn(null).when(service).nodeContent(1L, "SEGMENT", 1);
        doReturn("段零中摘").when(service).nodeContent(1L, "SEGMENT", 0);
        stubRecentChapters(List.of("章十五详摘"));

        String rendered = service.renderTreeDigest(1L, 50L);

        assertTrue(rendered.contains("【前情脉络】段零中摘"), "当前段缺位回退上一段");
    }

    @Test
    void 空树渲染返回null() {
        assertNull(service.renderTreeDigest(null, 1L));
        assertNull(service.renderTreeDigest(1L, 1L), "空树：交由调用方回退滚动摘要");
    }

    @Test
    void 定长不变式_50章渲染恒定上界() {
        // R-02 修复验证：滚动摘要随章数膨胀稀释；树渲染无论第几章，输出恒 ≤ 定长上界
        chapter(100L, 50);
        doReturn("书".repeat(1000)).when(service).nodeContent(1L, "BOOK", 0);      // LLM 无视指令的防御
        doReturn("段".repeat(2000)).when(service).nodeContent(1L, "SEGMENT", 4);
        stubRecentChapters(List.of("甲".repeat(900), "乙".repeat(900), "丙".repeat(900)));

        String rendered = service.renderTreeDigest(1L, 100L);

        // 上界 = 300(书) + 600(段) + 3×500(章) + 头部标记 ≈ 2500
        assertTrue(rendered.length() <= 2500,
                "渲染恒定上界（实测 " + rendered.length() + "）：不随章数线性膨胀");
    }
}
