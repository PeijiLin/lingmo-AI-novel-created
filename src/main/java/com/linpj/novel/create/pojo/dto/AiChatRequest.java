package com.linpj.novel.create.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author HL
 */
@Data
public class AiChatRequest implements Serializable {
    Long bookId;
    String userInput;
    Long chapterId;
    String creationType;
    String title;
    String worldSetting;
    String style;
}
