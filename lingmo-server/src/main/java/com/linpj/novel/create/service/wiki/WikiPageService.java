package com.linpj.novel.create.service.wiki;

import com.linpj.novel.create.pojo.entity.NovelWikiPage;

import java.util.List;

/**
 * Wiki 正典页服务（M3：CRUD / 版本 / 别名 / 状态）
 *
 * @author HL
 */
public interface WikiPageService {

    /** 新建页（同书同 entity_type+entity_key 幂等：已存在则返回已有页） */
    NovelWikiPage upsertPage(NovelWikiPage page, List<String> aliases);

    /** 编辑页卡：canonical/attrs/title 变更走版本 +1；状态流转单独方法 */
    NovelWikiPage editPage(Long pageId, String title, String canonical, Object attrs);

    /** 状态流转：DRAFT→CANON / CANON→DEPRECATED（DEPRECATED 不可复活——改设新页） */
    NovelWikiPage changeStatus(Long pageId, String targetStatus);

    /** 按页取别名 */
    List<String> aliasesOf(Long pageId);

    /** 按页 ID 取页（不存在抛 404 业务异常） */
    NovelWikiPage getPage(Long pageId);

    /** 别名定位（归一化后精确匹配；命中返回页，未命中返回 null） */
    NovelWikiPage locateByAlias(Long bookId, String name);

    /** 本书全部有效页（status != DEPRECATED，供页卡注入与列表） */
    List<NovelWikiPage> listCanonPages(Long bookId);

    /** 按实体类型过滤的有效页 */
    List<NovelWikiPage> listCanonPages(Long bookId, String entityType);

    /** 页卡渲染（provenance 标记 [wiki:{id}]，供 Critic/引文回指；硬 attrs 永不截断） */
    String renderPages(Long bookId, List<Long> pageIds);

    /** 删除页（级联清别名/锚点；调用方自决是否清 SETTING chunk） */
    void deletePage(Long pageId);
}
