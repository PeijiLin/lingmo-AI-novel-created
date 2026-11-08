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
    public Long createRunningTask(Long bookId, Long chapterId, String mode) {
        CreationTask task = new CreationTask();
        task.setBookId(bookId);
        task.setChapterId(chapterId);
        task.setMode(mode);
        task.setStatus("RUNNING");
        task.setDegraded(false);
        task.setTotalTokens(0);
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
    public void failTask(Long taskId, String currentStage, String error) {
        CreationTask task = new CreationTask();
        task.setId(taskId);
        task.setStatus("FAILED");
        task.setCurrentStage(currentStage);
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

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}
