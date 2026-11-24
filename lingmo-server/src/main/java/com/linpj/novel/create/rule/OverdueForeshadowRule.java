package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.BookChapter;
import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.pipeline.PlotOperation;
import com.linpj.novel.create.service.BookChapterService;
import com.linpj.novel.create.service.PlotThreadService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RC-09 伏笔逾期未回收（设计 6.7：软告警）——B1-3 伏笔排期（deadline_offset）的消费端
 *
 * 判定（账本级）：OPEN 伏笔满足已过章数（当前章号 - 埋设章号）&gt; 截止章距 → 逾期告警。
 * 在 OUTLINE 时机执行（细纲预审）：本章细纲已声明 RESOLVE 的伏笔跳过——写作意图已经在路上。
 *
 * 严重级取 LOW（软告警）：伏笔逾期是提醒性质，不应触发重写回路（HIGH/MEDIUM 才触发——设计 5.3）；
 * 与 v2.0 融合稿 6.7 表"medium/软"的表述差异属有意取舍：并入阻塞级会把"结构提醒"放大成"重写触发"。
 *
 * 必须诚实声明的局限（默认 OFF，影子态观测后再分级）：
 * - 截止章距由 Planner 声明（plannedIn/deadlineIn），声明缺失的伏笔无逾期概念（现状行为不变）
 * - 章号解析依赖 book_chapter.chapter_num；解析失败宁漏勿误
 * - 逾期判定发生在"本章规划时"，账本尚未消费本章的 RESOLVE 操作——故以细纲声明为准入豁免；
   未带 threadId 的 RESOLVE 模糊引用不纳入豁免（召回风险大于收益）
 *
 * @author HL
 */
@Component
public class OverdueForeshadowRule implements ConsistencyRule {

    /** 单次校验最多上报数 */
    private static final int MAX_VIOLATIONS = 5;

    private final PlotThreadService plotThreadService;
    private final BookChapterService bookChapterService;

    public OverdueForeshadowRule(PlotThreadService plotThreadService,
                                 BookChapterService bookChapterService) {
        this.plotThreadService = plotThreadService;
        this.bookChapterService = bookChapterService;
    }

    @Override
    public String id() {
        return "RC-09";
    }

    @Override
    public List<RuleStage> stages() {
        return List.of(RuleStage.OUTLINE);
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.LOW;
    }

    @Override
    public String description() {
        return "伏笔超截止章距未回收（排期账本告警，建议性——不触发重写）";
    }

    @Override
    public List<RuleViolation> check(RuleCheckContext context) {
        if (context.getBookId() == null || context.getChapterId() == null) {
            return List.of();
        }
        List<NovelPlotThread> threads;
        try {
            threads = plotThreadService.listActiveThreads(context.getBookId());
        } catch (Exception e) {
            return List.of(); // 数据通道故障：规则静默
        }
        if (threads == null || threads.isEmpty()) {
            return List.of();
        }
        // 只看声明了截止章距的伏笔（排期属 Planner 新契约，存量伏笔无此概念）
        List<NovelPlotThread> scheduled = threads.stream()
                .filter(Objects::nonNull)
                .filter(thread -> thread.getDeadlineOffset() != null && thread.getPlantedChapterId() != null)
                .toList();
        if (scheduled.isEmpty()) {
            return List.of();
        }
        Integer currentNum = resolveChapterNum(context.getChapterId());
        if (currentNum == null) {
            return List.of();
        }
        Map<Long, Integer> plantedNums = scheduled.stream()
                .map(NovelPlotThread::getPlantedChapterId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), this::resolveChapterNum));
        Set<Long> resolvedNow = resolveDeclaredThreadIds(context);

        List<RuleViolation> violations = new ArrayList<>();
        for (NovelPlotThread thread : scheduled) {
            if (violations.size() >= MAX_VIOLATIONS) {
                break;
            }
            if (resolvedNow.contains(thread.getId())) {
                continue; // 本章细纲已安排回收：不告警
            }
            Integer plantedNum = plantedNums.get(thread.getPlantedChapterId());
            if (plantedNum == null) {
                continue;
            }
            int elapsed = currentNum - plantedNum;
            if (elapsed > thread.getDeadlineOffset()) {
                violations.add(RuleViolation.of(id(), severity(), RuleStage.OUTLINE,
                        String.format("伏笔#%d「%s」已过 %d 章未回收（截止 %d 章内）：读者遗忘风险，建议本章或近章安排回收",
                                thread.getId(), trimDescription(thread.getDescription()), elapsed,
                                thread.getDeadlineOffset()),
                        "在细纲中声明该伏笔的 RESOLVE 操作，或显式 ABANDONED 弃置（弃置也应有交代）", false));
            }
        }
        return violations;
    }

    /** 本章细纲 RESOLVE 声明：已声明的 threadId 不作逾期告警（写作意图在路上的豁免通道） */
    private static Set<Long> resolveDeclaredThreadIds(RuleCheckContext context) {
        if (context.getOutline() == null || context.getOutline().getPlotOps() == null) {
            return Set.of();
        }
        return context.getOutline().getPlotOps().stream()
                .filter(Objects::nonNull)
                .filter(op -> op.getOp() != null && "RESOLVE".equalsIgnoreCase(op.getOp().trim()))
                .map(PlotOperation::getThreadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Integer resolveChapterNum(Long chapterId) {
        try {
            BookChapter chapter = bookChapterService.getById(chapterId);
            return chapter == null ? null : chapter.getChapterNum();
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimDescription(String description) {
        if (description == null) {
            return "";
        }
        return description.length() > 20 ? description.substring(0, 20) + "…" : description;
    }
}
