package com.linpj.novel.create.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * @author HL
 */
@Data
public class BookCategoryVo implements Serializable {

    private Long id;

    /**
     * 作品方向（男频 / 女频 / 自定义）
     */
    private String workDirection;

    /**
     * 名称（对齐前端 categoryName）
     */
    private String categoryName;
}
