package com.linpj.novel.create.pojo.vo;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author HL
 */
@Data
public class KnowledgeBaseVo implements Serializable {
    /**
     * 主键
     */
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

    /**
     * 文档id
     */
    private Object documentIds;

    /**
     * 创建时间
     */
    private Date createAt;

    /**
     * 修改时间
     */
    private Date updateAt;
}
