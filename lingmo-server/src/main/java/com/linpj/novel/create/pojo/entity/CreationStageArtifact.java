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
 * 阶段产物（每阶段结构化产物实时落库，异常中断后产物仍在）
 * @TableName creation_stage_artifact
 *
 * @author HL
 */
@TableName(value = "creation_stage_artifact", autoResultMap = true)
@Data
public class CreationStageArtifact implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** PLANNING / WRITING / CRITIC / POLISHING */
    private String stage;

    /** 重写轮次（首轮=1） */
    private Integer round;

    /** 结构化产物 JSON：outline / draft / review / polished */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object artifact;

    /** 本阶段 token 用量 */
    private Integer tokens;

    /** 本阶段耗时（毫秒） */
    private Long elapsedMs;

    private Date createdAt;
}
