package com.linpj.novel.create.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新建作品请求
 * @author HL
 */
@Data
public class BookCreateRequest implements Serializable {

    /**
     * 书籍名称
     */
    private String bookName;

    /**
     * 书籍描述
     */
    private String bookDesc;

    /**
     * 作品方向（男频 / 女频 / 自定义，如自由文本）
     */
    private String workDirection;

    /**
     * 作者ID（可选：缺省时由服务端从当前登录用户自动关联/创建作者）
     */
    private Long authorId;
}