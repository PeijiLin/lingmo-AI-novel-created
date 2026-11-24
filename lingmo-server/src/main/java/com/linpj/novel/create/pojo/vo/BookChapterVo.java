package com.linpj.novel.create.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author HL
 */
@Data
public class BookChapterVo implements Serializable {
    private Long id;

    /**
     *
     */
    private Long bookId;

    /**
     *
     */
    private Integer chapterNum;

    /**
     *
     */
    private String chapterName;

    /**
     *
     */
    private Integer wordCount;

    /**
     * 1-收费 0-免费
     */
    private Integer isVip;

    /**
     *
     */
    private Date createTime;
}
