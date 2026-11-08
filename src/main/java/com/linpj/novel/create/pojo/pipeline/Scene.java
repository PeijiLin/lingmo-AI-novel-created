package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 场景（大纲的最小叙事单元，每个场景必须有冲突）
 *
 * @author HL
 */
@Data
public class Scene implements Serializable {

    /** 场景地点 */
    private String location;

    /** 参与人物角色 ID 列表 */
    private java.util.List<Long> participantIds;

    /** 本场景冲突 */
    private String conflict;

    /** 场景结果 */
    private String outcome;
}
