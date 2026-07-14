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
     * 作品方向 0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 名称
     */
    private String name;
}
