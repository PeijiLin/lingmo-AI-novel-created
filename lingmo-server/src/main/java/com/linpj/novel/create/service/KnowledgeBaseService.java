package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linpj.novel.create.pojo.common.PageRequest;
import com.linpj.novel.create.pojo.dto.KnowledgeBaseDto;
import com.linpj.novel.create.pojo.dto.KnowledgeBasePageRequest;
import com.linpj.novel.create.pojo.entity.KnowledgeBase;
import com.linpj.novel.create.pojo.entity.NovelKbSource;
import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.vo.KnowledgeBaseVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
* @author HL
* @description 针对表【knowledge_base(知识库信息表)】的数据库操作Service
* @createDate 2025-11-22 16:50:25
*/
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    Long addKnowledgeBase(KnowledgeBaseDto knowledgeBaseDto);

    Page<KnowledgeBaseVo> pageKnowledgeBase(KnowledgeBasePageRequest pageRequest);

    /** 本书素材来源清单（novel_kb_source，M2） */
    List<NovelKbSource> listSources(Long bookId);

    /** 删除单个素材（级联清 chunk + 向量，M2） */
    void removeSource(Long sourceId);

    /** 删除知识库并级联清理本书素材/块/向量（M2） */
    void removeKnowledgeBaseCascade(Long knowledgeBaseId);
}
