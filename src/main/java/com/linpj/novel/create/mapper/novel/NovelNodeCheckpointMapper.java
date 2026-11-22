package com.linpj.novel.create.mapper.novel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linpj.novel.create.pojo.entity.NovelNodeCheckpoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点 checkpoint Mapper（M5-F2 断点续跑账本）
 *
 * @author HL
 */
@Mapper
public interface NovelNodeCheckpointMapper extends BaseMapper<NovelNodeCheckpoint> {
}
