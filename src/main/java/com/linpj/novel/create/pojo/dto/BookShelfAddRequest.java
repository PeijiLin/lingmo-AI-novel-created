package com.linpj.novel.create.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author HL
 */
@Data
public class BookShelfAddRequest implements Serializable {
    private Long bookId;
    private Long userId;
}
