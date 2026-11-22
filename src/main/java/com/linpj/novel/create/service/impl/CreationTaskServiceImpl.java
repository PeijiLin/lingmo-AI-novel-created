package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.CreationTaskMapper;
import com.linpj.novel.create.pojo.entity.CreationTask;
import com.linpj.novel.create.service.CreationTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 创作任务服务实现（统计埋点：每阶段 token 用量、耗时，为成本面板打底）
 *
 * @author HL
 */
@Slf4j
@Service
public class CreationTaskServiceImpl extends ServiceImpl<CreationTaskMapper, CreationTask>
        implements CreationTaskService {

    @Override
    public Long createRunningTask(Long bookId, Long chapterId, String mode, Object params) {
        CreationTask task = new CreationTask();
        task.setBookId(bookId);
        task.setChapterId(chapterId);
        task.setMode(mode);
        task.setStatus("RUNNING");
        task.setDegraded(false);
        task.setTotalTokens(0);
        task.setParams(params);
        save(task);
        return task.getId();
    }

    @Override
    public Long createPendingTask(Long bookId, Long chapterId, String mode, Object params) {
        CreationTask task = new CreationTask();
        task.setBookId(bookId);
        task.setChapterId(chapterId);
        task.setMode(mode);
        task.setStatus("PENDING");
        task.setDegraded(false);
        task.setTotalTokens(0);
        task.setParams(params);
        save(task);
        return task.getId();
    }

    @Override
    public boolean markRunningIfPending(Long taskId) {
        // 原子 CAS：仅 PENDING 状态可推进为 RUNNING，重复投递/多实例并发下只成功一次
        return this.update(new LambdaUpdateWrapper<CreationTask>()
                .eq(CreationTask::getId, taskId)
                .eq(CreationTask::getStatus, "PENDING")
                .set(CreationTask::getStatus, "RUNNING"));
    }

    @Override
    public void completeTask(Long taskId, boolean degraded, Integer totalTokens, Long elapsedMs) {
        CreationTask task = new CreationTask();
        task.setId(taskId);
        task.setStatus("COMPLETED");
        task.setDegraded(degraded);
        task.setTotalTokens(totalTokens);
        task.setElapsedMs(elapsedMs);
        updateById(task);
    }

    @Override
    public void failTask(Long taskId, String error) {
        CreationTask task = new CreationTask();
        task.setId(taskId);
        task.setStatus("FAILED");
        // current_stage 不赋值：updateById 按 null 跳过，保留最后推进阶段供断点续跑
        task.setError(truncate(error));
        updateById(task);
    }

    @Override
    public void advanceStage(Long taskId, String currentStage) {
        CreationTask task = new CreationTask();
        task.setId(taskId);
        task.setCurrentStage(currentStage);
        task.setStatus("RUNNING");
        updateById(task);
    }

    @Override
    public long sumBookTokens(Long bookId) {
        if (bookId == null) {
            return 0;
        }
        try {
            // 聚合在库侧完成；FAILED 任务的消耗同样计入（烧掉的钱不因任务失败而消失）
            java.util.List<Object> rows = this.listObjs(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CreationTask>()
                            .select("COALESCE(SUM(total_tokens), 0)")
                            .eq("book_id", bookId));
            if (rows == null || rows.isEmpty() || rows.get(0) == null) {
                return 0;
            }
            return ((Number) rows.get(0)).longValue();
        } catch (Exception e) {
            log.warn("书级 token 汇总失败（预算查询按 0 处理）: bookId={}, error={}", bookId, e.getMessage());
            return 0;
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}
