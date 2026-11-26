package com.linpj.novel.create.service.wiki;

import java.util.List;

/**
 * Wiki 初始化（M3：存量设定 → 正典页的一次性引导）
 *
 * <p>数据源：角色卡（character_cards）→ CHARACTER 页；书籍世界观（book_info.world_setting）
 * → WORLD_RULE 页（整段一个页，硬维度结构化留给 M4/P2-6）。
 * 作者可在页面编辑校对后转正。
 *
 * @author HL
 */
public interface WikiBootstrapService {

    /** 初始化结果（幂等：重复执行只补缺，不覆盖已编辑内容） */
    record BootstrapResult(int characterPages, int worldRulePages, int settingChunks) {
    }

    /**
     * 为本书初始化正典页：角色卡 → CHARACTER 页（别名=角色名），世界观 → WORLD_RULE 页。
     * 已存在的 entity_key 跳过——编辑过的页卡不被初始化覆盖。
     */
    BootstrapResult bootstrap(Long bookId);
}
