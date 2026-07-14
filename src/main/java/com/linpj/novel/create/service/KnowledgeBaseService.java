package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.pojo.common.PageRequest;
import com.linpj.novel.create.pojo.dto.KnowledgeBaseDto;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.vo.KnowledgeBaseVo;
import org.springframework.web.multipart.MultipartFile;

/**
* @author HL
* @description 针对表【knowledge_base(知识库信息表)】的数据库操作Service
* @createDate 2025-11-22 16:50:25
*/
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    Long addKnowledgeBase(KnowledgeBaseDto knowledgeBaseDto);

    Page<KnowledgeBaseVo> pageKnowledgeBase(PageRequest pageRequest);
}
