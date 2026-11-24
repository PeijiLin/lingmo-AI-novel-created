package com.linpj.novel.create.pojo.dto;

import com.linpj.novel.create.pojo.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author HL
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ChapterPageRequest extends PageRequest implements Serializable {
    private Long bookInfoId;
}
