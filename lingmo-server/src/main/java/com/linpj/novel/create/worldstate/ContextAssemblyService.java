package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.linpj.novel.create.pojo.entity.NovelWikiPage;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import com.linpj.novel.create.service.wiki.WikiPageService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文组装服务（设计 5.2）：所有 Agent 的上下文统一从这里出，消灭"前端传 worldSetting"模式
 *
 * - worldSetting 以 book_info.world_setting 为唯一事实源；前端仍可传，仅当书籍未配置时生效（覆盖提示）
 * - story_digest / 活跃伏笔 / 人物动态状态 / 章节大纲从 World State 账本组装
 * - 伏笔渲染双口径（B1-8，v2.0 4.4 可见性矩阵 - H4 隔离）：
 *     Writer      → renderActiveThreads（只知"埋什么"，不知"何时收"——防提前兑付）
 *     Planner/Critic → renderPlannerThreads（含回收排期与逾期提醒：规划与审查需要 meta）
 * - 长度预算硬控：总上下文约 8k token（中文 1 字 ≈ 1 token 折算），超预算按优先级裁剪：
 *   风格说明 < story_digest 截断 < 非出场角色 < 历史伏笔；出场角色卡与本章大纲永不裁
 *
 * @author HL
 */
@Service
public class ContextAssemblyService {

    /** 总上下文字符预算（约 8k token） */
    private static final int CONTEXT_CHAR_BUDGET = 8000;

    /** story_digest 截断保留长度（超预算时） */
    private static final int DIGEST_TRUNCATE_KEEP = 600;

    /** 极端兜底：世界观设定硬上限（预算 2/5——超出部分对长篇是持续性 token 财务负担） */
    private static final int WORLD_SETTING_HARD_CAP = CONTEXT_CHAR_BUDGET * 2 / 5;

    /** 极端兜底：角色信息最小保留额度（创作基准不可压到零） */
    private static final int CHARACTER_INFO_MIN_KEEP = 200;

    private final CharacterCardsService characterCardsService;
    private final BookInfoService bookInfoService;
    private final StoryDigestService storyDigestService;
    private final PlotThreadService plotThreadService;
    private final CharacterStateService characterStateService;
    private final CharacterRelationshipsService characterRelationshipsService;
    private final BookChapterService bookChapterService;
    private final WikiPageService wikiPageService;

    public ContextAssemblyService(CharacterCardsService characterCardsService,
                                  BookInfoService bookInfoService,
                                  StoryDigestService storyDigestService,
                                  PlotThreadService plotThreadService,
                                  CharacterStateService characterStateService,
                                  CharacterRelationshipsService characterRelationshipsService,
                                  BookChapterService bookChapterService,
                                  WikiPageService wikiPageService) {
        this.characterCardsService = characterCardsService;
        this.bookInfoService = bookInfoService;
        this.storyDigestService = storyDigestService;
        this.plotThreadService = plotThreadService;
        this.characterStateService = characterStateService;
        this.characterRelationshipsService = characterRelationshipsService;
        this.bookChapterService = bookChapterService;
        this.wikiPageService = wikiPageService;
    }

    // ------------------------------------------------------------------
    // Writer 上下文
    // ------------------------------------------------------------------

    /**
     * 组装 Writer 上下文
     *
     * @param request 管道请求
     * @param outline 章节大纲（Planner 产物，可空 = Planner 降级直写）
     */
    public WriterContext assembleWriterContext(PipelineRequest request, ChapterOutline outline) {
        WriterContext context = new WriterContext();
        context.setBookId(request.getBookId());
        context.setChapterId(request.getChapterId());
        context.setUserInput(request.getUserInput());
        context.setCreationType(request.getCreationType());
        context.setTitle(request.getTitle());
        context.setOutline(outline);
        context.setWorldSetting(resolveWorldSetting(request));
        context.setStyle(request.getStyle());
        context.setStoryDigest(storyDigestService.getDigest(request.getBookId()));
        // M3：正典页卡前置（硬 attrs + provenance 标记 [wiki:id]）——优先于角色卡渲染
        context.setWorldSetting(prepend(
                renderWikiPages(request.getBookId(), castIds(outline)),
                context.getWorldSetting()));
        context.setCharacterInfo(loadCharacterInfo(request.getBookId(), castIds(outline)));
        context.setCharacterInfo(prepend(
                renderRelationships(request.getBookId(), request.getChapterId(), castIds(outline)),
                context.getCharacterInfo()));
        context.setActivePlotThreads(plotThreadService.renderActiveThreads(request.getBookId()));
        appendCharacterStates(context, request.getBookId(), castIds(outline));
        trimToBudget(context);
        return context;
    }

    // ------------------------------------------------------------------
    // Critic 上下文
    // ------------------------------------------------------------------

    public CriticContext assembleCriticContext(PipelineRequest request, String draft, ChapterOutline outline) {
        CriticContext context = new CriticContext();
        context.setBookId(request.getBookId());
        context.setDraft(draft);
        context.setTitle(request.getTitle());
        context.setWorldSetting(resolveWorldSetting(request));
        // M3：正典页卡前置（Critic 审查基准同样以正典为准）
        context.setWorldSetting(prepend(
                renderWikiPages(request.getBookId(), castIds(outline)),
                context.getWorldSetting()));
        context.setCharacterInfo(loadCharacterInfo(request.getBookId(), castIds(outline)));
        context.setCharacterInfo(prepend(
                renderRelationships(request.getBookId(), request.getChapterId(), castIds(outline)),
                context.getCharacterInfo()));
        context.setStoryDigest(storyDigestService.getDigest(request.getBookId()));
        // meta 口径：Critic 审查"是否按排期兑付伏笔"需要知道排期（可见性矩阵 ✅）
        context.setActivePlotThreads(plotThreadService.renderPlannerThreads(
                request.getBookId(), request.getChapterId()));
        context.setOutline(outline);
        context.setUserInput(request.getUserInput());
        context.setCreationType(request.getCreationType());
        context.setStyle(request.getStyle());
        trimToBudget(context);
        return context;
    }

    // ------------------------------------------------------------------
    // Planner 上下文
    // ------------------------------------------------------------------

    public PlannerContext assemblePlannerContext(PipelineRequest request) {
        PlannerContext context = new PlannerContext();
        context.setBookId(request.getBookId());
        context.setChapterId(request.getChapterId());
        context.setUserInput(request.getUserInput());
        context.setTitle(request.getTitle());
        context.setWorldSetting(resolveWorldSetting(request));
        // M3：正典页卡前置（Planner 规划同样以正典为准）
        context.setWorldSetting(prepend(
                renderWikiPages(request.getBookId(), null),
                context.getWorldSetting()));
        context.setCharacterInfo(loadCharacterInfo(request.getBookId(), null));
        context.setCharacterInfo(prepend(
                renderRelationships(request.getBookId(), request.getChapterId(), null),
                context.getCharacterInfo()));
        context.setStoryDigest(storyDigestService.getDigest(request.getBookId()));
        // meta 口径：Planner 规划回收时点需要排期与逾期提醒（逾期回灌，B1-3/B1-8）
        context.setActivePlotThreads(plotThreadService.renderPlannerThreads(
                request.getBookId(), request.getChapterId()));
        context.setCreationType(request.getCreationType());
        context.setStyle(request.getStyle());
        trimToBudget(context);
        return context;
    }

    // ------------------------------------------------------------------
    // worldSetting 唯一事实源
    // ------------------------------------------------------------------

    /**
     * 世界观解析：优先读 book_info.world_setting（服务端唯一事实源）；
     * 书籍未配置时回退前端传入值（覆盖提示语义）。
     */
    private String resolveWorldSetting(PipelineRequest request) {
        if (request.getBookId() != null) {
            BookInfo bookInfo = bookInfoService.getById(request.getBookId());
            if (bookInfo != null && notBlank(bookInfo.getWorldSetting())) {
                return bookInfo.getWorldSetting();
            }
        }
        return request.getWorldSetting();
    }

    // ------------------------------------------------------------------
    // 角色卡 + 动态状态
    // ------------------------------------------------------------------

    private List<Long> castIds(ChapterOutline outline) {
        return outline != null && outline.getCharacterIds() != null
                && !outline.getCharacterIds().isEmpty() ? outline.getCharacterIds() : null;
    }

    /**
     * 加载角色卡（带角色 ID，供大纲/审查引用）。
     * 有出场角色清单时仅加载出场角色（预算裁剪：非出场角色优先裁）。
     */
    private String loadCharacterInfo(Long bookId, List<Long> castIds) {
        if (bookId == null) {
            return null;
        }
        List<CharacterCardsVo> characters = characterCardsService.getCharacterCardsByProjectId(bookId);
        if (characters == null || characters.isEmpty()) {
            return "无角色设定信息。";
        }
        List<CharacterCardsVo> selected = characters;
        if (castIds != null) {
            selected = characters.stream().filter(c -> castIds.contains(c.getId())).toList();
            if (selected.isEmpty()) {
                selected = characters; // 大纲引用的角色卡缺失：回退全量
            }
        }
        return selected.stream()
                .map(c -> String.format(
                        "角色ID: %d\n角色名: %s\n描述: %s\n性格: %s\n外貌: %s\n背景: %s\n能力: %s\n弱点: %s\n目标: %s\n---\n",
                        c.getId(), c.getName(), c.getDescription(), c.getPersonality(), c.getAppearance(),
                        c.getBackground(), c.getAbilities(), c.getWeaknesses(), c.getGoals()))
                .reduce("", String::concat);
    }

    /** 关系块前置到角色信息头部：预算兜底截断保头时，关系不会被先裁掉 */
    private static String prepend(String block, String body) {
        if (block == null || block.isBlank()) {
            return body;
        }
        return body == null || body.isBlank() ? block : block + "\n" + body;
    }

    /**
     * 渲染正典页卡（M3：黑板的权威依据，ADR-2 正典层）。
     * 出场角色对应 CHARACTER 页必注入；非 CHARACTER 页（WORLD_RULE 等）全量注入。
     * 预算兜底时硬 attrs 不裁（canonical 描述可截断）——renderPages 内部已实现分层截断。
     */
    private String renderWikiPages(Long bookId, List<Long> castIds) {
        if (bookId == null) {
            return null;
        }
        List<NovelWikiPage> pages;
        try {
            pages = wikiPageService.listCanonPages(bookId);
        } catch (Exception e) {
            return null; // Wiki 通道故障：宁缺勿错，不阻塞创作
        }
        if (pages == null || pages.isEmpty()) {
            return null;
        }
        // 出场角色过滤：CHARACTER 页按 castIds 取，其余类型全量
        List<Long> pageIds;
        if (castIds != null && !castIds.isEmpty()) {
            pageIds = pages.stream()
                    .filter(page -> !"CHARACTER".equals(page.getEntityType())
                            || (page.getEntityId() != null && castIds.contains(page.getEntityId())))
                    .map(NovelWikiPage::getId)
                    .toList();
        } else {
            pageIds = pages.stream().map(NovelWikiPage::getId).toList();
        }
        if (pageIds.isEmpty()) {
            return null;
        }
        return wikiPageService.renderPages(bookId, pageIds);
    }

    /**
     * 渲染当前生效的角色关系边（P0：让模型看到“谁和谁是什么关系”，减少称谓/立场幻觉）。
     * 章号不可得时只注入长期有效边；出场角色过滤沿用大纲口径。
     */
    private String renderRelationships(Long bookId, Long chapterId, List<Long> castIds) {
        if (bookId == null) {
            return null;
        }
        Integer chapterNum = resolveChapterNum(chapterId);
        List<CharacterRelationships> edges;
        try {
            edges = characterRelationshipsService.listActive(bookId, chapterNum);
        } catch (Exception e) {
            return null; // 关系通道故障：宁缺勿错，不阻塞创作
        }
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        if (castIds != null && !castIds.isEmpty()) {
            edges = edges.stream()
                    .filter(edge -> castIds.contains(edge.getFromCharacterId())
                            || castIds.contains(edge.getToCharacterId()))
                    .toList();
        }
        if (edges.isEmpty()) {
            return null;
        }
        Map<Long, String> names = new LinkedHashMap<>();
        try {
            List<CharacterCardsVo> cards = characterCardsService.getCharacterCardsByProjectId(bookId);
            if (cards != null) {
                cards.stream().filter(c -> c.getId() != null && notBlank(c.getName()))
                        .forEach(c -> names.put(c.getId(), c.getName()));
            }
        } catch (Exception e) {
            // 角色名解析失败时退化为角色 ID，不影响关系语义
        }
        String header = chapterNum == null
                ? "【长期有效角色关系】"
                : "【当前生效角色关系（第" + chapterNum + "章）】";
        List<CharacterRelationships> sorted = edges.stream()
                .sorted(Comparator.comparing((CharacterRelationships e) -> nameOf(names, e.getFromCharacterId()))
                        .thenComparing(e -> nameOf(names, e.getToCharacterId())))
                .toList();
        StringBuilder sb = new StringBuilder(header).append('\n');
        for (CharacterRelationships edge : sorted) {
            if (edge.getRelationshipType() == null || edge.getRelationshipType().isBlank()) {
                continue;
            }
            sb.append("- ")
                    .append(nameOf(names, edge.getFromCharacterId()))
                    .append(" → ")
                    .append(nameOf(names, edge.getToCharacterId()))
                    .append("：")
                    .append(edge.getRelationshipType());
            if (notBlank(edge.getDescription())) {
                sb.append("（").append(truncate(edge.getDescription(), 80)).append("）");
            }
            if (edge.getIntensity() != null) {
                sb.append(" [强度").append(edge.getIntensity()).append(']');
            }
            sb.append('\n');
        }
        sb.append("（关系以此为准，不得自行新增或改写；关系变化须由剧情推进并由归档更新）\n");
        return sb.toString();
    }

    private Integer resolveChapterNum(Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        try {
            BookChapter chapter = bookChapterService.getById(chapterId);
            return chapter == null ? null : chapter.getChapterNum();
        } catch (Exception e) {
            return null;
        }
    }

    private static String nameOf(Map<Long, String> names, Long id) {
        if (id == null) {
            return "未知角色";
        }
        String name = names.get(id);
        return name == null ? "角色#" + id : name;
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…";
    }

    /** 出场角色动态状态并入 characterInfo（角色卡静态设定 + character_state 当前态） */
    private void appendCharacterStates(WriterContext context, Long bookId, List<Long> castIds) {
        String states = characterStateService.renderStates(bookId, castIds);
        if (notBlank(states)) {
            context.setCharacterInfo(
                    (notBlank(context.getCharacterInfo()) ? context.getCharacterInfo() + "\n" : "")
                            + "【出场角色当前动态状态】\n" + states);
        }
    }

    // ------------------------------------------------------------------
    // 长度预算硬控（裁剪顺序从后往前：风格 < digest 截断 < 非出场角色 < 历史伏笔）
    // ------------------------------------------------------------------

    private void trimToBudget(WriterContext context) {
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        // 1. 裁风格说明
        context.setStyle(null);
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        // 2. story_digest 截断（保留最近的尾部）
        context.setStoryDigest(truncateDigest(context.getStoryDigest()));
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        // 3. 非出场角色：已在 loadCharacterInfo 按出场过滤，无可再裁（出场角色卡永不裁）
        // 4. 裁历史伏笔
        context.setActivePlotThreads(null);
        // 5. 极端兜底（TC-01 回归修复：设定/角色集本身超预算时必须压住，
        //    否则长篇的账本与卡池增长会让上下文无限膨胀）
        worldSettingHardCap(context);
        characterInfoHardCap(context, totalChars(context) - CONTEXT_CHAR_BUDGET);
    }

    /** Critic 上下文预算：待审正文与大纲为审查基准永不裁，其余同优先级裁剪 */
    private void trimToBudget(CriticContext context) {
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        context.setStyle(null);
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        context.setStoryDigest(truncateDigest(context.getStoryDigest()));
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        // 无大纲时角色卡为全量事实基准，不裁；裁历史伏笔
        context.setActivePlotThreads(null);
        // 极端兜底（同 Writer）：正典页块完整保留，只截原始设定
        context.setWorldSetting(wikiAwareHardCap(context.getWorldSetting()));
        characterInfoHardCap(context, totalChars(context) - CONTEXT_CHAR_BUDGET);
    }

    /** Planner 上下文预算：角色列表是 characterIds 的选择基准永不裁 */
    private void trimToBudget(PlannerContext context) {
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        context.setStyle(null);
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        context.setStoryDigest(truncateDigest(context.getStoryDigest()));
        if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
            return;
        }
        // 裁历史伏笔（角色列表为选择基准，永不裁）
        context.setActivePlotThreads(null);
        // 极端兜底（同 Writer）：正典页块完整保留，只截原始设定
        context.setWorldSetting(wikiAwareHardCap(context.getWorldSetting()));
        characterInfoHardCap(context, totalChars(context) - CONTEXT_CHAR_BUDGET);
    }

    /**
     * 带正典块感知的硬截断：worldSetting = [wiki 页块]\n[原始设定]。
     * 页块完整保留（超长时单独截其 canonical 描述已在 renderPages 内完成）；
     * 原始设定压到剩余额度，额度耗尽也保留头部标记。
     */
    static String wikiAwareHardCap(String worldSetting) {
        if (worldSetting == null || worldSetting.length() <= WORLD_SETTING_HARD_CAP) {
            return worldSetting;
        }
        int split = worldSetting.indexOf("\n");
        boolean wikiFirst = worldSetting.startsWith("【正典设定页") && split > 0;
        if (!wikiFirst) {
            return hardCapStatic(worldSetting, WORLD_SETTING_HARD_CAP);
        }
        String wikiBlock = worldSetting.substring(0, split);
        String original = worldSetting.substring(split + 1);
        int remaining = Math.max(WORLD_SETTING_HARD_CAP - wikiBlock.length(), 0);
        if (original.length() <= remaining) {
            return worldSetting;
        }
        if (remaining == 0) {
            return wikiBlock; // 极端：页块本身吃满预算，原始设定整体丢弃
        }
        return wikiBlock + "\n……" + original.substring(original.length() - remaining);
    }

    /** 静态硬截断（保头部 + 截断标记；null/未超限原样返回） */
    private static String hardCapStatic(String text, int cap) {
        if (text == null || text.length() <= cap || cap <= 0) {
            return text;
        }
        String marker = "\n……(设定过长已截断，完整版见书籍设定)";
        return text.substring(0, Math.max(0, cap - marker.length())) + marker;
    }

    /** story_digest 截断（保留最近尾部；已短于保留长度时不变） */
    private static String truncateDigest(String digest) {
        if (digest == null || digest.length() <= DIGEST_TRUNCATE_KEEP) {
            return digest;
        }
        return "……" + digest.substring(digest.length() - DIGEST_TRUNCATE_KEEP);
    }

    // ------------------------------------------------------------------
    // 极端兜底硬上限（TC-01：设定/角色池本身超预算时必须压住，防长篇持续膨胀）
    // ------------------------------------------------------------------

    /**
     * 世界观硬上限：正典页块（以【正典设定页开头标记）完整保留，只截后方的原始 worldSetting。
     * 硬 attrs 永不裁是 M3 的注入承诺——截断只作用于自由文本。
     */
    private void worldSettingHardCap(WriterContext context) {
        context.setWorldSetting(wikiAwareHardCap(context.getWorldSetting()));
    }

    /** Writer 角色信息兜底截断（保头部：角色列表与出场状态在头部，头部是最重要信息） */
    private void characterInfoHardCap(WriterContext context, int overflow) {
        if (overflow <= 0) {
            return;
        }
        int currentLen = len(context.getCharacterInfo());
        context.setCharacterInfo(hardCap(context.getCharacterInfo(),
                Math.max(currentLen - overflow, CHARACTER_INFO_MIN_KEEP)));
    }

    /** Critic/Planner 角色信息兜底截断 */
    private void characterInfoHardCap(CriticContext context, int overflow) {
        if (overflow <= 0) {
            return;
        }
        int currentLen = len(context.getCharacterInfo());
        context.setCharacterInfo(hardCap(context.getCharacterInfo(),
                Math.max(currentLen - overflow, CHARACTER_INFO_MIN_KEEP)));
    }

    private void characterInfoHardCap(PlannerContext context, int overflow) {
        if (overflow <= 0) {
            return;
        }
        int currentLen = len(context.getCharacterInfo());
        context.setCharacterInfo(hardCap(context.getCharacterInfo(),
                Math.max(currentLen - overflow, CHARACTER_INFO_MIN_KEEP)));
    }

    /** 硬截断（保头部 + 截断标记；null/未超限原样返回） */
    private static String hardCap(String text, int cap) {
        if (text == null || text.length() <= cap || cap <= 0) {
            return text;
        }
        String marker = "\n……(设定过长已截断，完整版见书籍设定)";
        return text.substring(0, Math.max(0, cap - marker.length())) + marker;
    }

    private int totalChars(WriterContext context) {
        return len(context.getWorldSetting()) + len(context.getCharacterInfo())
                + len(context.getStoryDigest()) + len(context.getActivePlotThreads())
                + len(context.getStyle()) + len(context.getUserInput());
    }

    private int totalChars(CriticContext context) {
        return len(context.getWorldSetting()) + len(context.getCharacterInfo())
                + len(context.getStoryDigest()) + len(context.getActivePlotThreads())
                + len(context.getStyle()) + len(context.getUserInput()) + len(context.getDraft());
    }

    private int totalChars(PlannerContext context) {
        return len(context.getWorldSetting()) + len(context.getCharacterInfo())
                + len(context.getStoryDigest()) + len(context.getActivePlotThreads())
                + len(context.getStyle()) + len(context.getUserInput());
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 兼容重载：现有 FAST 链路 AiChatRequest（NovelAICreationService 后续可切换到此） */
    public WriterContext assembleWriterContext(AiChatRequest request) {
        return assembleWriterContext(PipelineRequest.from(request), null);
    }
}
