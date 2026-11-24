package com.linpj.novel.create.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author HL
 */
@Data
public class KnowledgeBaseDto implements Serializable {
    /**
     * 主键
     */
    @TableId
    private Long id;

    /**
     * 创作书籍id
     */
    private Long bookId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 知识库名字
     */
    private String name;

    /**
     * 描述
     */
    private String description;
}
