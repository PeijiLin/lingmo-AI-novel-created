package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelSummaryTreeMapper;
import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.NovelSummaryTree;
import com.linpj.novel.create.service.BookChapterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 摘要树服务（M5-B4：定长摘要树账本——纯持久化与渲染，LLM 调用由 ChapterFinalizeService 编排）
 *
 * 三层结构（定长不变式由 {@link #truncate} 强制，LLM 输出超限截断）：
 * - CHAPTER：章节详摘（≤500 字/章）
 * - SEGMENT：区段中摘（每 {@link #SEGMENT_SIZE} 章一节点，≤600 字）
 * - BOOK：全书短摘（≤300 字，段边界重算）
 *
 * 渲染口径（供滚动摘要派生与装配消费）：全书短摘 + 最近段中摘 + 近 {@link #RECENT_CHAPTERS} 章详摘。
 *
 * @author HL
 */
@Service
public class SummaryTreeService extends ServiceImpl<NovelSummaryTreeMapper, NovelSummaryTree> {

    private static final Logger log = LoggerFactory.getLogger(SummaryTreeService.class);

    public static final String LEVEL_CHAPTER = "CHAPTER";
    public static final String LEVEL_SEGMENT = "SEGMENT";
    public static final String LEVEL_BOOK = "BOOK";

    /** 区段跨度（章）：D-3 决策取区段节点而非 Volume 实体——最小实现 */
    public static final int SEGMENT_SIZE = 10;

    /** 渲染保留的近章详摘数 */
    public static final int RECENT_CHAPTERS = 3;

    /** 定长上限（字符≈token 1:1 折算，与装配器同款口径） */
    public static final int CHAPTER_MAX_CHARS = 500;
    public static final int SEGMENT_MAX_CHARS = 600;
    public static final int BOOK_MAX_CHARS = 300;

    private final BookChapterService bookChapterService;

    /** 兼容构造（无章号解析依赖：测试/离线装配用，chapterId 解析将降级） */
    public SummaryTreeService() {
        this.bookChapterService = null;
    }

    /** 生产装配（Spring 唯一注入点） */
    @Autowired
    public SummaryTreeService(BookChapterService bookChapterService) {
        this.bookChapterService = bookChapterService;
    }

    // ------------------------------------------------------------------
    // 写入（chapterId -> chapter_num 域解析；章号不可得返回 null 由调用方回退）
    // ------------------------------------------------------------------

    /**
     * 章节详摘入账（upsert，幂等：同节点重算 version+1）。
     *
     * @return 章号（账本定位成功）；章号解析失败返回 null（调用方回退滚动摘要路径）
     */
    public Integer recordChapterDigest(Long bookId, Long chapterId, String digest) {
        if (bookId == null || chapterId == null || digest == null || digest.isBlank()) {
            return null;
        }
        Integer chapterNum = resolveChapterNum(chapterId);
        if (chapterNum == null) {
            log.warn("章节号解析失败，摘要树跳过该章: bookId={}, chapterId={}", bookId, chapterId);
            return null;
        }
        upsertNode(bookId, LEVEL_CHAPTER, chapterNum, truncate(digest, CHAPTER_MAX_CHARS), chapterId);
        // 血缘标记：下层更新 -> 上层 stale（段即时重算、书按段边界重算，见 ChapterFinalizeService）
        markAncestorsStale(bookId, chapterNum);
        return chapterNum;
    }

    /** 区段中摘 upsert（seq=区段序号；重算调用方负责内容生成与截断） */
    public void upsertNode(Long bookId, String level, Integer seq, String content) {
        upsertNode(bookId, level, seq, content, null);
    }

    private void upsertNode(Long bookId, String level, Integer seq, String content, Long chapterId) {
        NovelSummaryTree existing = findNode(bookId, level, seq);
        NovelSummaryTree node = existing != null ? existing : new NovelSummaryTree();
        node.setBookId(bookId);
        node.setLevel(level);
        node.setSeq(seq);
        node.setChapterId(chapterId);
        node.setContent(content);
        node.setIsStale(Boolean.FALSE);
        node.setVersion(existing != null ? existing.getVersion() + 1 : 1);
        node.setUpdatedAt(new java.util.Date());
        if (existing != null) {
            this.updateById(node);
        } else {
            this.save(node);
        }
    }

    /** 下层写入时置上层血缘 stale（段与书；渲染不受影响——始终取最新内容，stale 仅标记血缘） */
    private void markAncestorsStale(Long bookId, int chapterNum) {
        markStale(bookId, LEVEL_SEGMENT, segmentOf(chapterNum));
        markStale(bookId, LEVEL_BOOK, 0);
    }

    private void markStale(Long bookId, String level, Integer seq) {
        NovelSummaryTree node = findNode(bookId, level, seq);
        if (node != null && !Boolean.TRUE.equals(node.getIsStale())) {
            node.setIsStale(Boolean.TRUE);
            this.updateById(node);
        }
    }

    // ------------------------------------------------------------------
    // 读取（供 LLM 压缩源与渲染）
    // ------------------------------------------------------------------

    /** 区段序号（0 起：章 1-10 -> 段 0，章 11-20 -> 段 1） */
    public static int segmentOf(int chapterNum) {
        return Math.max(0, chapterNum - 1) / SEGMENT_SIZE;
    }

    /** 指定区段内已入账的章节详摘（按章序升序；LLM 区段压缩的输入） */
    public List<String> loadChapterDigestsOfSegment(Long bookId, int segmentIndex) {
        int from = segmentIndex * SEGMENT_SIZE + 1;
        int to = from + SEGMENT_SIZE - 1;
        return loadChapterDigests(bookId, from, to);
    }

    /** 区间内章节详摘（升序；渲染的近章窗口与压缩源共用） */
    public List<String> loadChapterDigests(Long bookId, int fromChapterNum, int toChapterNum) {
        List<NovelSummaryTree> nodes = this.list(nodeQuery(bookId, LEVEL_CHAPTER)
                .ge(NovelSummaryTree::getSeq, fromChapterNum)
                .le(NovelSummaryTree::getSeq, toChapterNum)
                .orderByAsc(NovelSummaryTree::getSeq));
        return digestTexts(nodes);
    }

    /** 全部区段中摘（升序；书短摘压缩源） */
    public List<String> loadSegmentDigests(Long bookId) {
        return digestTexts(this.list(nodeQuery(bookId, LEVEL_SEGMENT)
                .orderByAsc(NovelSummaryTree::getSeq)));
    }

    /** 指定节点内容（无节点/null 故障返回 null） */
    public String nodeContent(Long bookId, String level, Integer seq) {
        NovelSummaryTree node = findNode(bookId, level, seq);
        return node == null ? null : node.getContent();
    }

    /** 节点是否存在（书短摘"是否缺席"判定用，避免 content 空串歧义） */
    public boolean nodeExists(Long bookId, String level, Integer seq) {
        return findNode(bookId, level, seq) != null;
    }

    // ------------------------------------------------------------------
    // 渲染（定长不变式的最终执行点：拼装 + 硬截断）
    // ------------------------------------------------------------------

    /**
     * 渲染摘要树为装配可用文本：全书短摘(≤300) + 最近段中摘(≤600) + 近 3 章详摘(各≤500)。
     * 恒定上界 ≈ 2400 字 + 头部标记——不随章数膨胀（H2 的机制基础）。
     *
     * @return 无任何可用节点时 null（调用方回退）；章节不可定位时仅渲染段/书层
     */
    public String renderTreeDigest(Long bookId, Long currentChapterId) {
        if (bookId == null) {
            return null;
        }
        Integer currentNum = currentChapterId == null ? null : resolveChapterNum(currentChapterId);
        StringBuilder sb = new StringBuilder();

        String book = nodeContent(bookId, LEVEL_BOOK, 0);
        if (book != null && !book.isBlank()) {
            sb.append("【全书概览】").append(truncate(book, BOOK_MAX_CHARS)).append('\n');
        }
        Integer segmentSeq = currentNum == null ? null : segmentOf(currentNum);
        if (segmentSeq != null) {
            String segment = nodeContent(bookId, LEVEL_SEGMENT, segmentSeq);
            if (segment == null || segment.isBlank()) {
                // 当前段尚未生成（首 10 章内或段重算未跑）：回退上一段
                segment = segmentSeq > 0 ? nodeContent(bookId, LEVEL_SEGMENT, segmentSeq - 1) : null;
            }
            if (segment != null && !segment.isBlank()) {
                sb.append("【前情脉络】").append(truncate(segment, SEGMENT_MAX_CHARS)).append('\n');
            }
        }
        if (currentNum != null) {
            // 近 3 章详摘：窗口 [currentNum-2, currentNum] 升序拼接（时间顺序读感）
            int from = Math.max(1, currentNum - (RECENT_CHAPTERS - 1));
            List<String> chapters = loadChapterDigests(bookId, from, currentNum);
            if (!chapters.isEmpty()) {
                sb.append("【最近章节】");
                for (int i = 0; i < chapters.size(); i++) {
                    if (i > 0) {
                        sb.append(' ');
                    }
                    sb.append(truncate(chapters.get(i), CHAPTER_MAX_CHARS));
                }
                sb.append('\n');
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 定位单节点（包可见：单测内存仓的桩接缝——生产仅本类消费） */
    NovelSummaryTree findNode(Long bookId, String level, Integer seq) {
        if (seq == null) {
            return null;
        }
        return this.getOne(nodeQuery(bookId, level).eq(NovelSummaryTree::getSeq, seq), false);
    }

    private static LambdaQueryWrapper<NovelSummaryTree> nodeQuery(Long bookId, String level) {
        return new LambdaQueryWrapper<NovelSummaryTree>()
                .eq(NovelSummaryTree::getBookId, bookId)
                .eq(NovelSummaryTree::getLevel, level);
    }

    private static List<String> digestTexts(List<NovelSummaryTree> nodes) {
        List<String> texts = new ArrayList<>();
        if (nodes == null) {
            return texts;
        }
        nodes.stream()
                .filter(node -> node != null && node.getContent() != null && !node.getContent().isBlank())
                .forEach(node -> texts.add(node.getContent()));
        return texts;
    }

    /** 章号解析（BookChapter 缺位/故障返回 null——账本定位失败宁可跳过也不猜） */
    private Integer resolveChapterNum(Long chapterId) {
        if (bookChapterService == null || chapterId == null) {
            return null;
        }
        try {
            BookChapter chapter = bookChapterService.getById(chapterId);
            return chapter == null ? null : chapter.getChapterNum();
        } catch (Exception e) {
            log.warn("章号解析失败（摘要树跳过）: chapterId={}, error={}", chapterId, e.getMessage());
            return null;
        }
    }

    /** 定长截断（字符域；中文 1 字 ≈ 1 token 折算，与装配预算同款口径） */
    static String truncate(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }
}
