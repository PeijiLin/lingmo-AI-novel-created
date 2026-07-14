package com.linpj.novel.create.pojo.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author HL
 */
@Data
public class PageRequest implements Serializable {
    protected Integer pageNum = 1;
    protected Integer pageSize = 10;
    protected Boolean sort = false;
}
