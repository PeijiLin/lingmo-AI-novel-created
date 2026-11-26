package com.linpj.novel.create.service.wiki.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.novel.NovelWikiAliasMapper;
import com.linpj.novel.create.mapper.novel.NovelWikiAnchorMapper;
import com.linpj.novel.create.mapper.novel.NovelWikiPageMapper;
import com.linpj.novel.create.pojo.entity.NovelWikiAlias;
import com.linpj.novel.create.pojo.entity.NovelWikiAnchor;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.service.wiki.WikiPageService;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wiki 正典页服务实现（M3）
 *
 * <p>写入口唯一性：作者编辑与 Bootstrap 初始化都走本服务——单一写者约定的 Wiki 侧执行点。
 *
 * @author HL
 */
@Service
public class WikiPageServiceImpl implements WikiPageService {

    private static final String STATUS_CANON = "CANON";
    private static final String STATUS_DEPRECATED = "DEPRECATED";

    private final NovelWikiPageMapper pageMapper;
    private final NovelWikiAliasMapper aliasMapper;
    private final NovelWikiAnchorMapper anchorMapper;

    public WikiPageServiceImpl(NovelWikiPageMapper pageMapper,
                               NovelWikiAliasMapper aliasMapper,
                               NovelWikiAnchorMapper anchorMapper) {
        this.pageMapper = pageMapper;
        this.aliasMapper = aliasMapper;
        this.anchorMapper = anchorMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelWikiPage upsertPage(NovelWikiPage page, List<String> aliases) {
        validate(page);
        NovelWikiPage existing = pageMapper.selectOne(new LambdaQueryWrapper<NovelWikiPage>()
                .eq(NovelWikiPage::getBookId, page.getBookId())
                .eq(NovelWikiPage::getEntityType, page.getEntityType())
                .eq(NovelWikiPage::getEntityKey, page.getEntityKey())
                .last("limit 1"));
        if (existing != null) {
            return existing; // 幂等：初始化重复执行不产生重复页
        }
        page.setId(null);
        page.setStatus(STATUS_CANON);
        page.setVersion(1);
        page.setCreatedAt(new Date());
        page.setUpdatedAt(new Date());
        pageMapper.insert(page);
        replaceAliases(page.getBookId(), page.getId(), aliases);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NovelWikiPage editPage(Long pageId, String title, String canonical, Object attrs) {
        NovelWikiPage page = requirePage(pageId);
        boolean changed = false;
        if (title != null && !title.isBlank() && !title.equals(page.getTitle())) {
            page.setTitle(title);
            changed = true;
        }
        if (canonical != null && !canonical.equals(page.getCanonical())) {
            page.setCanonical(canonical);
            changed = true;
        }
        if (attrs != null) {
            page.setAttrs(attrs);
            changed = true;
        }
        if (changed) {
            page.setVersion(page.getVersion() == null ? 1 : page.getVersion() + 1);
            page.setUpdatedAt(new Date());
            pageMapper.updateById(page);
        }
        return page;
    }

    @Override
    public NovelWikiPage changeStatus(Long pageId, String targetStatus) {
        NovelWikiPage page = requirePage(pageId);
        if (STATUS_DEPRECATED.equals(page.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已废弃页不可变更状态；如需恢复设定请新建页");
        }
        if (STATUS_CANON.equals(targetStatus) || STATUS_DEPRECATED.equals(targetStatus)) {
            page.setStatus(targetStatus);
            page.setUpdatedAt(new Date());
            pageMapper.updateById(page);
            return page;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的状态: " + targetStatus);
    }

    @Override
    public List<String> aliasesOf(Long pageId) {
        return aliasMapper.selectList(new LambdaQueryWrapper<NovelWikiAlias>()
                        .eq(NovelWikiAlias::getPageId, pageId))
                .stream().map(NovelWikiAlias::getAlias).toList();
    }

    @Override
    public NovelWikiPage getPage(Long pageId) {
        return requirePage(pageId);
    }

    @Override
    public NovelWikiPage locateByAlias(Long bookId, String name) {
        if (bookId == null || name == null || name.isBlank()) {
            return null;
        }
        NovelWikiAlias alias = aliasMapper.selectOne(new LambdaQueryWrapper<NovelWikiAlias>()
                .eq(NovelWikiAlias::getBookId, bookId)
                .eq(NovelWikiAlias::getNormalized, normalize(name))
                .last("limit 1"));
        if (alias == null) {
            return null;
        }
        return pageMapper.selectById(alias.getPageId());
    }

    @Override
    public List<NovelWikiPage> listCanonPages(Long bookId) {
        return pageMapper.selectList(new LambdaQueryWrapper<NovelWikiPage>()
                .eq(NovelWikiPage::getBookId, bookId)
                .ne(NovelWikiPage::getStatus, STATUS_DEPRECATED)
                .orderByAsc(NovelWikiPage::getId));
    }

    @Override
    public List<NovelWikiPage> listCanonPages(Long bookId, String entityType) {
        return pageMapper.selectList(new LambdaQueryWrapper<NovelWikiPage>()
                .eq(NovelWikiPage::getBookId, bookId)
                .eq(NovelWikiPage::getEntityType, entityType)
                .ne(NovelWikiPage::getStatus, STATUS_DEPRECATED)
                .orderByAsc(NovelWikiPage::getId));
    }

    @Override
    public String renderPages(Long bookId, List<Long> pageIds) {
        if (bookId == null || pageIds == null || pageIds.isEmpty()) {
            return null;
        }
        List<NovelWikiPage> pages = pageMapper.selectBatchIds(pageIds);
        if (pages.isEmpty()) {
            return null;
        }
        Map<Long, Set<String>> aliasesByPage = new LinkedHashMap<>();
        aliasMapper.selectList(new LambdaQueryWrapper<NovelWikiAlias>()
                        .in(NovelWikiAlias::getPageId, pageIds))
                .forEach(alias -> aliasesByPage
                        .computeIfAbsent(alias.getPageId(), k -> new LinkedHashSet<>())
                        .add(alias.getAlias()));
        StringBuilder sb = new StringBuilder("【正典设定页（以此为准，未列出的事实不得自行新增）】\n");
        for (NovelWikiPage page : pages) {
            if (STATUS_DEPRECATED.equals(page.getStatus())) {
                continue;
            }
            Set<String> aliases = aliasesByPage.getOrDefault(page.getId(), Set.of());
            sb.append("- [wiki:").append(page.getId()).append("] ")
                    .append(page.getTitle());
            if (!aliases.isEmpty()) {
                sb.append("（又称：").append(String.join("、", aliases)).append("）");
            }
            sb.append('\n');
            if (page.getAttrs() != null) {
                sb.append("  硬事实: ").append(serializeAttrs(page.getAttrs())).append('\n');
            }
            if (page.getCanonical() != null && !page.getCanonical().isBlank()) {
                sb.append("  ").append(truncate(page.getCanonical(), 300)).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePage(Long pageId) {
        requirePage(pageId);
        aliasMapper.delete(new LambdaQueryWrapper<NovelWikiAlias>()
                .eq(NovelWikiAlias::getPageId, pageId));
        anchorMapper.delete(new LambdaQueryWrapper<NovelWikiAnchor>()
                .eq(NovelWikiAnchor::getPageId, pageId));
        pageMapper.deleteById(pageId);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private NovelWikiPage requirePage(Long pageId) {
        ThrowsUtils.throwIf(pageId == null || pageId <= 0, ErrorCode.PARAMS_ERROR, "无效的页ID");
        NovelWikiPage page = pageMapper.selectById(pageId);
        ThrowsUtils.throwIf(page == null, ErrorCode.NOT_FOUND_ERROR, "正典页不存在");
        return page;
    }

    private static void validate(NovelWikiPage page) {
        ThrowsUtils.throwIf(page == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(page.getBookId() == null || page.getBookId() <= 0,
                ErrorCode.PARAMS_ERROR, "书籍ID不能为空");
        ThrowsUtils.throwIf(page.getEntityType() == null || page.getEntityType().isBlank(),
                ErrorCode.PARAMS_ERROR, "实体类型不能为空");
        ThrowsUtils.throwIf(page.getEntityKey() == null || page.getEntityKey().isBlank(),
                ErrorCode.PARAMS_ERROR, "实体键不能为空");
        ThrowsUtils.throwIf(page.getTitle() == null || page.getTitle().isBlank(),
                ErrorCode.PARAMS_ERROR, "标题不能为空");
    }

    /** 别名插入（书内唯一约束由 uk_wiki_alias 兜底：同别名挂他页时跳过，定位以先注册者为准） */
    private void replaceAliases(Long bookId, Long pageId, List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            NovelWikiAlias row = new NovelWikiAlias();
            row.setBookId(bookId);
            row.setPageId(pageId);
            row.setAlias(alias.strip());
            row.setNormalized(normalize(alias));
            row.setCreatedAt(new Date());
            try {
                aliasMapper.insert(row);
            } catch (Exception e) {
                // 重复别名：幂等跳过
            }
        }
    }

    /** 归一化：去空白、全角转半角、转小写 */
    static String normalize(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.strip().toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c >= '！' && c <= '～') {
                c -= 0xFEE0; // 全角转半角
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String serializeAttrs(Object attrs) {
        String json = attrs instanceof String s ? s : JsonUtil.toJson(attrs);
        return truncate(json, 200);
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }
}
