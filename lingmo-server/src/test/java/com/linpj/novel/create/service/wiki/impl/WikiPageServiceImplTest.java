package com.linpj.novel.create.service.wiki.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.linpj.novel.create.mapper.novel.NovelWikiAliasMapper;
import com.linpj.novel.create.mapper.novel.NovelWikiAnchorMapper;
import com.linpj.novel.create.mapper.novel.NovelWikiPageMapper;
import com.linpj.novel.create.pojo.entity.NovelWikiAlias;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiki 正典页服务单元测试：幂等 upsert / 版本递增 / 别名定位 / 页卡渲染
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WikiPageServiceImplTest {

    @Mock private NovelWikiPageMapper pageMapper;
    @Mock private NovelWikiAliasMapper aliasMapper;
    @Mock private NovelWikiAnchorMapper anchorMapper;

    private WikiPageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WikiPageServiceImpl(pageMapper, aliasMapper, anchorMapper);
        // insert 回填 id（模拟 BIGSERIAL）
        lenient().when(pageMapper.insert(any(NovelWikiPage.class))).thenAnswer(invocation -> {
            NovelWikiPage page = invocation.getArgument(0);
            page.setId(1L);
            return 1;
        });
        lenient().when(aliasMapper.insert(any(NovelWikiAlias.class))).thenReturn(1);
    }

    private NovelWikiPage page(String type, String key, String title) {
        NovelWikiPage page = new NovelWikiPage();
        page.setBookId(1L);
        page.setEntityType(type);
        page.setEntityKey(key);
        page.setTitle(title);
        page.setCanonical("页卡正文");
        page.setAttrs(Map.of("gender", "男"));
        return page;
    }

    // ------------------------------------------------------------------
    // upsert 幂等
    // ------------------------------------------------------------------

    @Test
    void upsertInsertsNewPageWithAliases() {
        when(pageMapper.selectOne(any())).thenReturn(null);

        NovelWikiPage saved = service.upsertPage(page("CHARACTER", "char:1", "林澈"),
                List.of("林澈", " 林澈 ", "小林"));

        assertEquals("CANON", saved.getStatus());
        assertEquals(1, saved.getVersion());
        verify(pageMapper).insert(any(NovelWikiPage.class));
        verify(aliasMapper, org.mockito.Mockito.times(3)).insert(any(NovelWikiAlias.class));
    }

    @Test
    void upsertReturnsExistingPageWithoutInsert() {
        NovelWikiPage existing = page("CHARACTER", "char:1", "林澈");
        existing.setId(9L);
        existing.setVersion(3);
        when(pageMapper.selectOne(any())).thenReturn(existing);

        NovelWikiPage saved = service.upsertPage(page("CHARACTER", "char:1", "林澈"), null);

        assertEquals(9L, saved.getId());
        assertEquals(3, saved.getVersion());
        verify(pageMapper, never()).insert(any(NovelWikiPage.class));
    }

    // ------------------------------------------------------------------
    // 版本递增
    // ------------------------------------------------------------------

    @Test
    void editPageBumpsVersionOnChange() {
        NovelWikiPage existing = page("CHARACTER", "char:1", "林澈");
        existing.setId(5L);
        existing.setVersion(2);
        when(pageMapper.selectById(5L)).thenReturn(existing);

        NovelWikiPage saved = service.editPage(5L, "林澈", "新版正文", null);

        assertEquals(3, saved.getVersion());
        assertEquals("新版正文", saved.getCanonical());
        verify(pageMapper).updateById(existing);
    }

    @Test
    void editPageNoChangeKeepsVersion() {
        NovelWikiPage existing = page("CHARACTER", "char:1", "林澈");
        existing.setId(5L);
        existing.setVersion(2);
        existing.setCanonical("正文");
        when(pageMapper.selectById(5L)).thenReturn(existing);

        service.editPage(5L, "林澈", "正文", null);

        assertEquals(2, existing.getVersion());
        verify(pageMapper, never()).updateById(any(NovelWikiPage.class));
    }

    // ------------------------------------------------------------------
    // 别名定位
    // ------------------------------------------------------------------

    @Test
    void locateMatchesNormalizedAlias() {
        NovelWikiAlias alias = new NovelWikiAlias();
        alias.setBookId(1L);
        alias.setPageId(33L);
        alias.setAlias("林澈");
        alias.setNormalized("林澈");
        when(aliasMapper.selectOne(any())).thenReturn(alias);
        NovelWikiPage target = page("CHARACTER", "char:1", "林澈");
        target.setId(33L);
        when(pageMapper.selectById(33L)).thenReturn(target);

        NovelWikiPage found = service.locateByAlias(1L, " 林澈 ");

        assertNotNull(found);
        assertEquals(33L, found.getId());
    }

    @Test
    void locateReturnsNullWhenMiss() {
        when(aliasMapper.selectOne(any())).thenReturn(null);

        assertNull(service.locateByAlias(1L, "不存在"));
    }

    // ------------------------------------------------------------------
    // 页卡渲染（provenance）
    // ------------------------------------------------------------------

    @Test
    void renderPagesCarriesProvenanceAndAliases() {
        NovelWikiPage p1 = page("CHARACTER", "char:1", "林澈");
        p1.setId(33L);
        p1.setCanonical("三清山弟子。");
        p1.setAttrs(Map.of("gender", "男"));
        when(pageMapper.selectBatchIds(anyList())).thenReturn(List.of(p1));
        NovelWikiAlias alias = new NovelWikiAlias();
        alias.setPageId(33L);
        alias.setAlias("小林");
        alias.setNormalized("小林");
        when(aliasMapper.selectList(any(Wrapper.class))).thenReturn(List.of(alias));

        String rendered = service.renderPages(1L, List.of(33L));

        assertNotNull(rendered);
        assertTrue(rendered.contains("[wiki:33]"), "provenance 标记");
        assertTrue(rendered.contains("小林"), "别名渲染");
        assertTrue(rendered.contains("硬事实"), "attrs 渲染");
        assertTrue(rendered.contains("三清山弟子"), "canonical 渲染");
        assertTrue(rendered.contains("不得自行新增"), "闭世界约束声明");
    }

    @Test
    void renderPagesSkipsDeprecated() {
        NovelWikiPage deprecated = page("CHARACTER", "char:1", "林澈");
        deprecated.setId(33L);
        deprecated.setStatus("DEPRECATED");
        when(pageMapper.selectBatchIds(anyList())).thenReturn(List.of(deprecated));

        String rendered = service.renderPages(1L, List.of(33L));

        assertTrue(rendered != null && !rendered.contains("[wiki:33]"), "废弃页不注入");
    }

    // ------------------------------------------------------------------
    // 归一化
    // ------------------------------------------------------------------

    @Test
    void normalizeStripsSpaceAndFullWidth() {
        assertEquals("林澈", WikiPageServiceImpl.normalize(" 林澈 "));
        assertEquals("abc123", WikiPageServiceImpl.normalize("Ａｂｃ１２３"));
        assertEquals("", WikiPageServiceImpl.normalize("  "));
    }
}
