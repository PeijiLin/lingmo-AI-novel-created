package com.linpj.novel.create.pojo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author HL
 */
@Data
public class CreateBookVo implements Serializable {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 书籍ID
     */
    private Long bookId;

    /**
     * 标题
     */
    private String title;

    /**
     * 编号
     */
    private Integer number;

    /**
     * 字数统计
     */
    private Integer wordCount;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 内容
     */
    private String content;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 笔记
     */
    private String notes;

    /**
     * 发布时间
     */
    private Date publishedTime;

    /**
     * 更新时间
     */
    private Date updatedTime;

}
