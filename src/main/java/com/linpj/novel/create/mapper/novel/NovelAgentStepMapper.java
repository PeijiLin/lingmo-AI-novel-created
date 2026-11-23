package com.linpj.novel.create.mapper.novel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linpj.novel.create.pojo.entity.NovelAgentStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * AgentRuntime 步骤账本 Mapper（自主性改造 R0：步骤级 Checkpoint）
 *
 * @author HL
 */
@Mapper
public interface NovelAgentStepMapper extends BaseMapper<NovelAgentStep> {
}
