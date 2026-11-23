package com.linpj.novel.create.mapper.novel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工具回执账本 Mapper（自主性改造 R0：真实回执驱动）
 *
 * @author HL
 */
@Mapper
public interface NovelToolReceiptMapper extends BaseMapper<NovelToolReceipt> {
}
