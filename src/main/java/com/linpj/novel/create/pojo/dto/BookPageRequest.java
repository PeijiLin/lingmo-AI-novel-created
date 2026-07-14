package com.linpj.novel.create.pojo.dto;

import com.linpj.novel.create.pojo.common.PageRequest;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.domain.Sort;

import java.io.Serializable;

@Getter
public class BookPageRequest extends PageRequest implements Serializable {
    /**
     * 0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 分类ID
     */
    private Long categoryId;

}
