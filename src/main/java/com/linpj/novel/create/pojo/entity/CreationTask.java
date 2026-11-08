package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linpj.novel.create.utils.JsonbTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 创作任务（一次管道执行的记录：状态推进 + token/耗时埋点，为成本面板打底；异步模式作为幂等栅栏）
 * @TableName creation_task
 *
 * @author HL
 */
@TableName(value = "creation_task", autoResultMap = true)
@Data
public class CreationTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long chapterId;

    /** FAST / STANDARD / REFINED / PRECISE */
    private String mode;

    /** PENDING / RUNNING / COMPLETED / FAILED（异步模式作为幂等栅栏） */
    private String status;

    /** 当前推进到的阶段（断点续跑预留） */
    private String currentStage;

    /** 是否发生降级 */
    private Boolean degraded;

    /** 全程 LLM token 用量 */
    private Integer totalTokens;

    /** 全程耗时（毫秒） */
    private Long elapsedMs;

    /** 失败原因 */
    private String error;

    /** 异步任务入参（AsyncCreationTaskMessage 序列化，崩溃重启后重建管道入参用） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object params;

    private Date createdAt;

    private Date updatedAt;
}
