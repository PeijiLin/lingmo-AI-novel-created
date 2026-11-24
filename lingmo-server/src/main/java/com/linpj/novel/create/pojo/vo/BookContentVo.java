package com.linpj.novel.create.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author HL
 */
@Data
public class BookContentVo implements Serializable {
    private Long id;

    /**
     *
     */
    private Long chapterId;

    /**
     *
     */
    private String content;
    private Date createTime;
}
