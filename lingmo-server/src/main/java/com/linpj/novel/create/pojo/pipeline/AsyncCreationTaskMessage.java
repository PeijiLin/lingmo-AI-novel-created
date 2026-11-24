package com.linpj.novel.create.pojo.pipeline;

import lombok.Data;

import java.io.Serializable;

/**
 * 异步创作任务消息（novel.creation.task topic 载荷）
 *
 * 携带完整管道参数：消费者崩溃重启后可从任务表 + 该消息重建管道入参。
 *
 * @author HL
 */
@Data
public class AsyncCreationTaskMessage implements Serializable {

    private Long taskId;

    private Long bookId;

    private Long chapterId;

    private String userInput;

    private String creationType;

    private String title;

    private String worldSetting;

    private String style;

    /** CreationMode 名称 */
    private String mode;

    private Integer maxRewriteRounds;

    public static AsyncCreationTaskMessage of(Long taskId, PipelineRequest request) {
        AsyncCreationTaskMessage message = new AsyncCreationTaskMessage();
        message.setTaskId(taskId);
        message.setBookId(request.getBookId());
        message.setChapterId(request.getChapterId());
        message.setUserInput(request.getUserInput());
        message.setCreationType(request.getCreationType());
        message.setTitle(request.getTitle());
        message.setWorldSetting(request.getWorldSetting());
        message.setStyle(request.getStyle());
        message.setMode(request.getMode().name());
        message.setMaxRewriteRounds(request.getMaxRewriteRounds());
        return message;
    }

    public PipelineRequest toRequest() {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(bookId);
        request.setChapterId(chapterId);
        request.setUserInput(userInput);
        request.setCreationType(creationType);
        request.setTitle(title);
        request.setWorldSetting(worldSetting);
        request.setStyle(style);
        request.setMode(CreationMode.valueOf(mode == null ? CreationMode.FAST.name() : mode));
        request.setMaxRewriteRounds(maxRewriteRounds == null ? 2 : maxRewriteRounds);
        return request;
    }
}
