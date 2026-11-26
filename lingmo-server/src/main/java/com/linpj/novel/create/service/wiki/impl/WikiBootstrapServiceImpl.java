package com.linpj.novel.create.service.wiki.impl;

import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.ingest.EmbeddingIndexService;
import com.linpj.novel.create.service.wiki.WikiBootstrapService;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiki 初始化实现（M3）
 *
 * <p>角色卡 → CHARACTER 页：canonical 由卡面字段拼装，attrs 提取白名单键（gender/age/occupation）。
 * 世界观 → WORLD_RULE 页：entity_key=rule:world_setting（每书一条），canonical=全文。
 * 页卡同步生成 SETTING 块（含 page_id），供检索路由"锚点下钻"主路径。
 *
 * @author HL
 */
@Service
public class WikiBootstrapServiceImpl implements WikiBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(WikiBootstrapServiceImpl.class);

    private final WikiPageService wikiPageService;
    private final CharacterCardsService characterCardsService;
    private final BookInfoService bookInfoService;
    private final EmbeddingIndexService embeddingIndexService;

    public WikiBootstrapServiceImpl(WikiPageService wikiPageService,
                                    CharacterCardsService characterCardsService,
                                    BookInfoService bookInfoService,
                                    EmbeddingIndexService embeddingIndexService) {
        this.wikiPageService = wikiPageService;
        this.characterCardsService = characterCardsService;
        this.bookInfoService = bookInfoService;
        this.embeddingIndexService = embeddingIndexService;
    }

    @Override
    public BootstrapResult bootstrap(Long bookId) {
        if (bookId == null || bookId <= 0) {
            return new BootstrapResult(0, 0, 0);
        }
        int characterPages = bootstrapCharacterPages(bookId);
        int worldRulePages = bootstrapWorldSettingPage(bookId);
        int settingChunks = rebuildSettingChunks(bookId);
        log.info("Wiki 初始化: bookId={}, characterPages={}, worldRulePages={}, settingChunks={}",
                bookId, characterPages, worldRulePages, settingChunks);
        return new BootstrapResult(characterPages, worldRulePages, settingChunks);
    }

    // ------------------------------------------------------------------
    // 角色卡 → CHARACTER 页
    // ------------------------------------------------------------------

    private int bootstrapCharacterPages(Long bookId) {
        List<CharacterCardsVo> cards;
        try {
            cards = characterCardsService.getCharacterCardsByProjectId(bookId);
        } catch (Exception e) {
            return 0; // 角色卡通道故障：跳过初始化，不阻塞
        }
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (CharacterCardsVo card : cards) {
            if (card == null || card.getId() == null || card.getName() == null) {
                continue;
            }
            NovelWikiPage page = new NovelWikiPage();
            page.setBookId(bookId);
            page.setEntityType("CHARACTER");
            page.setEntityId(card.getId());
            page.setEntityKey("char:" + card.getId());
            page.setTitle(card.getName());
            page.setCanonical(renderCharacterCanonical(card));
            page.setAttrs(whitelistAttrs(card));
            NovelWikiPage saved = wikiPageService.upsertPage(page, List.of(card.getName()));
            if (saved.getVersion() != null && saved.getVersion() == 1
                    && saved.getUpdatedAt() != null && saved.getCreatedAt() != null
                    && saved.getCreatedAt().equals(saved.getUpdatedAt())) {
                created++; // 本次新建（幂等区分：已存在页 version>1 或时间不同）
            }
        }
        return created;
    }

    private static String renderCharacterCanonical(CharacterCardsVo card) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "描述", card.getDescription());
        appendIfPresent(sb, "性格", card.getPersonality());
        appendIfPresent(sb, "外貌", card.getAppearance());
        appendIfPresent(sb, "背景", card.getBackground());
        appendIfPresent(sb, "能力", card.getAbilities());
        appendIfPresent(sb, "弱点", card.getWeaknesses());
        appendIfPresent(sb, "目标", card.getGoals());
        return sb.isEmpty() ? null : sb.toString();
    }

    /** 白名单 attrs（§3.4 首版只收确定性字段；自由文本留在 canonical） */
    private static Map<String, Object> whitelistAttrs(CharacterCardsVo card) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (card.getGender() != null && !card.getGender().isBlank()) {
            attrs.put("gender", card.getGender());
        }
        if (card.getAge() != null) {
            attrs.put("age", card.getAge());
        }
        if (card.getOccupation() != null && !card.getOccupation().isBlank()) {
            attrs.put("occupation", card.getOccupation());
        }
        return attrs.isEmpty() ? null : attrs;
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(label).append("：").append(value.strip());
    }

    // ------------------------------------------------------------------
    // 世界观 → WORLD_RULE 页
    // ------------------------------------------------------------------

    private int bootstrapWorldSettingPage(Long bookId) {
        BookInfo book;
        try {
            book = bookInfoService.getById(bookId);
        } catch (Exception e) {
            return 0;
        }
        if (book == null || book.getWorldSetting() == null || book.getWorldSetting().isBlank()) {
            return 0;
        }
        NovelWikiPage page = new NovelWikiPage();
        page.setBookId(bookId);
        page.setEntityType("WORLD_RULE");
        page.setEntityKey("rule:world_setting");
        page.setTitle("世界观总纲");
        page.setCanonical(book.getWorldSetting());
        NovelWikiPage saved = wikiPageService.upsertPage(page, List.of("世界观", "世界设定"));
        boolean isNew = saved.getVersion() != null && saved.getVersion() == 1
                && saved.getCreatedAt() != null && saved.getCreatedAt().equals(saved.getUpdatedAt());
        return isNew ? 1 : 0;
    }

    // ------------------------------------------------------------------
    // SETTING 块重建（页卡 → 检索主路径数据源）
    // ------------------------------------------------------------------

    /** 把有效页的 canonical 重新切块入向量（page_id 打标；先清旧 SETTING 块） */
    private int rebuildSettingChunks(Long bookId) {
        List<NovelWikiPage> pages = wikiPageService.listCanonPages(bookId);
        if (pages.isEmpty()) {
            return 0;
        }
        embeddingIndexService.deleteSettingChunks(bookId);
        int total = 0;
        for (NovelWikiPage page : pages) {
            if (page.getCanonical() == null || page.getCanonical().isBlank()) {
                continue;
            }
            total += embeddingIndexService.indexSettingContent(bookId, page.getId(), page.getCanonical());
        }
        return total;
    }
}
