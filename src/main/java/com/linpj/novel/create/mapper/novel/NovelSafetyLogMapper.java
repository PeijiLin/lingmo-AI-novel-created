package com.linpj.novel.create.mapper.novel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linpj.novel.create.pojo.entity.NovelSafetyLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全审计账本 Mapper
 *
 * @author HL
 */
@Mapper
public interface NovelSafetyLogMapper extends BaseMapper<NovelSafetyLog> {
}
