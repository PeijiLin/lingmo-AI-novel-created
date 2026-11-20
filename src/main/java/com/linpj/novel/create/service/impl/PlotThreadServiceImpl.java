package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelPlotThreadMapper;
import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.pipeline.PlotOperation;
import com.linpj.novel.create.service.PlotThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 伏笔账本服务实现
 *
 * @author HL
 */
@Service
public class PlotThreadServiceImpl extends ServiceImpl<NovelPlotThreadMapper, NovelPlotThread>
        implements PlotThreadService {

    private static final Logger log = LoggerFactory.getLogger(PlotThreadServiceImpl.class);

    /** 内联伏笔标记：<!--plot: 描述--> */
    private static final Pattern INLINE_PLOT_PATTERN = Pattern.compile("<!--plot:\\s*(.*?)\\s*-->", Pattern.DOTALL);

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_RESOLVED = "RESOLVED";

    @Override
    public List<NovelPlotThread> listActiveThreads(Long bookId) {
        if (bookId == null) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<NovelPlotThread>()
                .eq(NovelPlotThread::getBookId, bookId)
                .eq(NovelPlotThread::getStatus, STATUS_OPEN)
                .orderByAsc(NovelPlotThread::getId));
    }

    @Override
    public String renderActiveThreads(Long bookId) {
        List<NovelPlotThread> threads = listActiveThreads(bookId);
        if (threads.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (NovelPlotThread thread : threads) {
            sb.append("[伏笔#").append(thread.getId()).append("] ")
                    .append(thread.getDescription()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<NovelPlotThread> listSecretThreads(Long bookId) {
        if (bookId == null) {
            return List.of();
        }
        // whoKnows 3 NULL：JSONB 非 null 事实（含空数组），避免误把未维护线程当秘密
        return this.list(new LambdaQueryWrapper<NovelPlotThread>()
                .eq(NovelPlotThread::getBookId, bookId)
                .eq(NovelPlotThread::getStatus, STATUS_OPEN)
                .isNotNull(NovelPlotThread::getWhoKnows)
                .orderByAsc(NovelPlotThread::getId))
                .stream()
                .filter(thread -> !PlotThreadService.whoKnowsOf(thread).isEmpty())
                .toList();
    }

    @Override
    @Transactional
    public void applyPlotOperations(Long bookId, Long chapterId, List<PlotOperation> plotOps) {
        if (plotOps == null || plotOps.isEmpty() || bookId == null) {
            return;
        }
        for (PlotOperation op : plotOps) {
            if (op == null || op.getOp() == null) {
                continue;
            }
            switch (op.getOp().toUpperCase()) {
                case "PLANT" -> plantThread(bookId, chapterId, op);
                case "RESOLVE" -> resolveThread(bookId, chapterId, op);
                case "ADVANCE" -> advanceThread(bookId, op);
                default -> log.warn("未知伏笔操作类型，忽略: {}", op.getOp());
            }
        }
    }

    private void plantThread(Long bookId, Long chapterId, PlotOperation op) {
        if (op.getThreadId() != null) {
            // 引用既有伏笔再次声明：视为 ADVANCE，不重复入账
            advanceThread(bookId, op);
            return;
        }
        String description = trimToNull(op.getDescription());
        if (description == null) {
            log.warn("PLANT 操作缺少 description，忽略");
            return;
        }
        if (existsDescription(bookId, description)) {
            return; // 幂等：同描述已存在
        }
        NovelPlotThread thread = new NovelPlotThread();
        thread.setBookId(bookId);
        thread.setDescription(description);
        thread.setPlantedChapterId(chapterId);
        thread.setStatus(STATUS_OPEN);
        thread.setWhoKnows(op.getWhoKnows());
        thread.setSecretKey(trimToNull(op.getSecretKey()));
        this.save(thread);
        log.info("伏笔入账[PLANT]: bookId={}, threadId={}, desc={}, whoKnows={}",
                bookId, thread.getId(), description, op.getWhoKnows());
    }

    private void resolveThread(Long bookId, Long chapterId, PlotOperation op) {
        Long threadId = op.getThreadId();
        if (threadId == null && trimToNull(op.getDescription()) != null) {
            // 未带 ID 的 RESOLVE：按描述模糊匹配最近的 OPEN 伏笔
            NovelPlotThread matched = this.getOne(new LambdaQueryWrapper<NovelPlotThread>()
                    .eq(NovelPlotThread::getBookId, bookId)
                    .eq(NovelPlotThread::getStatus, STATUS_OPEN)
                    .like(NovelPlotThread::getDescription, trimToNull(op.getDescription()))
                    .orderByDesc(NovelPlotThread::getId)
                    .last("LIMIT 1"));
            threadId = matched != null ? matched.getId() : null;
        }
        if (threadId == null) {
            log.warn("RESOLVE 操作无法定位伏笔（threadId 与描述均无效），忽略");
            return;
        }
        NovelPlotThread update = new NovelPlotThread();
        update.setId(threadId);
        update.setStatus(STATUS_RESOLVED);
        update.setResolvedChapterId(chapterId);
        update.setUpdatedAt(new Date());
        // 收束 = 合法揭穿时刻：把本次新增知情者并入集合（闭世界集合只在增长方向维护）
        mergeWhoKnows(threadId, op.getWhoKnows(), update);
        this.updateById(update);
        log.info("伏笔入账[RESOLVE]: bookId={}, threadId={}, chapterId={}", bookId, threadId, chapterId);
    }

    private void advanceThread(Long bookId, PlotOperation op) {
        if (op.getThreadId() == null) {
            return;
        }
        NovelPlotThread update = new NovelPlotThread();
        update.setId(op.getThreadId());
        update.setUpdatedAt(new Date());
        mergeWhoKnows(op.getThreadId(), op.getWhoKnows(), update);
        this.updateById(update);
    }

    /**
     * 知情集合并集合并（v2.0 M2-C5）：数据库旧集合 ∪ 入参新集合，回写 update 实体。
     * 闭世界集合只增不减（知情不可撤销——"忘了"也按风险面更大处理）；旧集合缺失时直接落数组。
     */
    private void mergeWhoKnows(Long threadId, List<Long> additions, NovelPlotThread update) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        try {
            NovelPlotThread existing = this.getById(threadId);
            java.util.Set<Long> merged = existing == null
                    ? new java.util.HashSet<>() : PlotThreadService.whoKnowsOf(existing);
            merged.addAll(additions);
            update.setWhoKnows(new java.util.ArrayList<>(merged));
        } catch (Exception e) {
            // 知情集合维护失败不阻正记账（规则侧宁可漏检不可阻断推进）
            log.warn("whoKnows 合并失败（忽略新增）: threadId={}, error={}", threadId, e.getMessage());
        }
    }

    @Override
    public List<String> extractInlinePlotMarks(String text) {
        List<String> marks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return marks;
        }
        Matcher matcher = INLINE_PLOT_PATTERN.matcher(text);
        while (matcher.find()) {
            String description = trimToNull(matcher.group(1));
            if (description != null) {
                marks.add(description);
            }
        }
        return marks;
    }

    @Override
    public String stripInlinePlotMarks(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return INLINE_PLOT_PATTERN.matcher(text).replaceAll("");
    }

    @Override
    public void recordInlinePlotMarks(Long bookId, Long chapterId, List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty() || bookId == null) {
            return;
        }
        for (String description : descriptions) {
            if (trimToNull(description) == null || existsDescription(bookId, description)) {
                continue;
            }
            NovelPlotThread thread = new NovelPlotThread();
            thread.setBookId(bookId);
            thread.setDescription(description);
            thread.setPlantedChapterId(chapterId);
            thread.setStatus(STATUS_OPEN);
            this.save(thread);
            log.info("伏笔入账[INLINE]: bookId={}, threadId={}, desc={}", bookId, thread.getId(), description);
        }
    }

    private boolean existsDescription(Long bookId, String description) {
        return this.count(new LambdaQueryWrapper<NovelPlotThread>()
                .eq(NovelPlotThread::getBookId, bookId)
                .eq(NovelPlotThread::getDescription, description)) > 0;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
