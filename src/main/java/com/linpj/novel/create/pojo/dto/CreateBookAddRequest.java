package com.linpj.novel.create.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author HL
 */
@Data
public class CreateBookAddRequest implements Serializable {
    /**
     *
     */
    private Long bookId;

    /**
     *
     */
    private String title;

    /**
     *
     */
    private Integer number;

    /**
     *
     */
    private Integer wordCount;

    /**
     *
     */
    private String content;

    /**
     *
     */
    private Long authorId;
}
