package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.pojo.entity.BookInfo;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.PlannerContext;
import com.linpj.novel.create.pojo.pipeline.WriterContext;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.PlotThreadService;
import com.linpj.novel.create.service.StoryDigestService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 上下文组装服务（设计 5.2）：所有 Agent 的上下文统一从这里出，消灭"前端传 worldSetting"模式
 *
 * - worldSetting 以 book_info.world_setting 为唯一事实源；前端仍可传，仅当书籍未配置时生效（覆盖提示）
 * - story_digest / 活跃伏笔 / 人物动态状态 / 章节大纲从 World State 账本组装
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

    private final CharacterCardsService characterCardsService;
    private final BookInfoService bookInfoService;
    private final StoryDigestService storyDigestService;
    private final PlotThreadService plotThreadService;
    private final CharacterStateService characterStateService;

    public ContextAssemblyService(CharacterCardsService characterCardsService,
                                  BookInfoService bookInfoService,
                                  StoryDigestService storyDigestService,
                                  PlotThreadService plotThreadService,
                                  CharacterStateService characterStateService) {
        this.characterCardsService = characterCardsService;
        this.bookInfoService = bookInfoService;
        this.storyDigestService = storyDigestService;
        this.plotThreadService = plotThreadService;
        this.characterStateService = characterStateService;
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
        context.setCharacterInfo(loadCharacterInfo(request.getBookId(), castIds(outline)));
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
        context.setCharacterInfo(loadCharacterInfo(request.getBookId(), castIds(outline)));
        context.setStoryDigest(storyDigestService.getDigest(request.getBookId()));
        context.setActivePlotThreads(plotThreadService.renderActiveThreads(request.getBookId()));
        context.setOutline(outline);
        context.setUserInput(request.getUserInput());
        context.setCreationType(request.getCreationType());
        context.setStyle(request.getStyle());
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
        context.setCharacterInfo(loadCharacterInfo(request.getBookId(), null));
        context.setStoryDigest(storyDigestService.getDigest(request.getBookId()));
        context.setActivePlotThreads(plotThreadService.renderActiveThreads(request.getBookId()));
        context.setCreationType(request.getCreationType());
        context.setStyle(request.getStyle());
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
        if (notBlank(context.getStoryDigest()) && context.getStoryDigest().length() > DIGEST_TRUNCATE_KEEP) {
            context.setStoryDigest("……" + context.getStoryDigest()
                    .substring(context.getStoryDigest().length() - DIGEST_TRUNCATE_KEEP));
            if (totalChars(context) <= CONTEXT_CHAR_BUDGET) {
                return;
            }
        }
        // 3. 非出场角色：已在 loadCharacterInfo 按出场过滤，无可再裁（出场角色卡永不裁）
        // 4. 裁历史伏笔
        context.setActivePlotThreads(null);
    }

    private int totalChars(WriterContext context) {
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
