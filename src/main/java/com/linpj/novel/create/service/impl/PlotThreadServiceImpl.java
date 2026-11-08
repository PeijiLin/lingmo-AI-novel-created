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
        this.save(thread);
        log.info("伏笔入账[PLANT]: bookId={}, threadId={}, desc={}", bookId, thread.getId(), description);
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
        this.updateById(update);
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
