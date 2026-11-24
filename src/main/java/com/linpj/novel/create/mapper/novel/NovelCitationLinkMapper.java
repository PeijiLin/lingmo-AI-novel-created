package com.linpj.novel.create.mapper.novel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linpj.novel.create.pojo.entity.NovelCitationLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * 引文绑定账本 Mapper（自主性改造 R1b：引文协议）
 *
 * @author HL
 */
@Mapper
public interface NovelCitationLinkMapper extends BaseMapper<NovelCitationLink> {
}
