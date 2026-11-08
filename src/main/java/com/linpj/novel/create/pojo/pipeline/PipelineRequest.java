package com.linpj.novel.create.pojo.pipeline;

import com.linpj.novel.create.pojo.dto.AiChatRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * 管道执行请求（由 AiChatRequest 派生，编排器唯一入参契约）
 *
 * @author HL
 */
@Data
public class PipelineRequest implements Serializable {

    private Long bookId;

    private Long chapterId;

    /** 用户输入（续写指令/草稿/关键词） */
    private String userInput;

    /** 创作类型：续写 / 对话 / 情节 / 描写 / 润色 / 灵感 */
    private String creationType;

    private String title;

    /**
     * 世界观（前端传入仅作覆盖提示，服务端以 book_info.world_setting 为唯一事实源；
     * 书籍未配置 world_setting 时使用该值）
     */
    private String worldSetting;

    private String style;

    /** 创作模式，默认 FAST */
    private CreationMode mode = CreationMode.FAST;

    /** 重写轮数上限，默认 2，硬上限 3 */
    private int maxRewriteRounds = 2;

    /** 由通用请求构建管道请求（mode/maxRewriteRounds 缺省走默认值） */
    public static PipelineRequest from(AiChatRequest request) {
        PipelineRequest pipelineRequest = new PipelineRequest();
        pipelineRequest.setBookId(request.getBookId());
        pipelineRequest.setChapterId(request.getChapterId());
        pipelineRequest.setUserInput(request.getUserInput());
        pipelineRequest.setCreationType(request.getCreationType());
        pipelineRequest.setTitle(request.getTitle());
        pipelineRequest.setWorldSetting(request.getWorldSetting());
        pipelineRequest.setStyle(request.getStyle());
        if (request.getMode() != null) {
            pipelineRequest.setMode(request.getMode());
        }
        if (request.getMaxRewriteRounds() != null) {
            // 硬上限 3：止损线不因入参放开
            pipelineRequest.setMaxRewriteRounds(Math.min(3, Math.max(1, request.getMaxRewriteRounds())));
        }
        return pipelineRequest;
    }
}
