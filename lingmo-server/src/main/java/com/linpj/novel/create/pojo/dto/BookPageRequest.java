package com.linpj.novel.create.pojo.dto;

import com.linpj.novel.create.pojo.common.PageRequest;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.domain.Sort;

import java.io.Serializable;

@Getter
public class BookPageRequest extends PageRequest implements Serializable {
    /**
     * 作品方向（男频 / 女频 / 自定义）
     */
    private String workDirection;

    /**
     * 分类ID
     */
    private Long categoryId;

}
