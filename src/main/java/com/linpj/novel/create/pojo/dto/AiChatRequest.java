package com.linpj.novel.create.pojo.dto;

import com.linpj.novel.create.pojo.pipeline.CreationMode;
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

    /**
     * 创作模式：FAST（默认，现有单链路）/ STANDARD / REFINED / PRECISE。
     * 老前端不传该字段即 FAST，五个现有端点行为不变。
     */
    CreationMode mode;

    /**
     * Critic 重写轮数上限（默认 2，硬上限 3），仅 STANDARD/REFINED/PRECISE 生效。
     */
    Integer maxRewriteRounds;
}
