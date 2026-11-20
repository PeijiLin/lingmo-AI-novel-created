package com.linpj.novel.create.pojo.dto;

import com.linpj.novel.create.pojo.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 知识库分页请求（含按 bookId 过滤，对齐前端 KnowledgeBasePageRequest）
 * @author HL
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeBasePageRequest extends PageRequest implements Serializable {
    /**
     * 创作书籍 id（可选，过滤该书知识库）
     */
    private Long bookId;
}