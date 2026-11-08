package com.linpj.novel.create.agent;

import com.linpj.novel.create.pojo.pipeline.CharacterStateSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 抽取 Agent（章节定稿后的账本维护节点）
 *
 * - rollUpDigest：章节摘要 -> 与旧摘要合并滚动更新 story_digest（一次额外调用）
 * - extractStates：从正文抽取出场人物动态状态 -> 增量维护 character_state（每章一次）
 *
 * 设计 5.3：抽取只做账本的"补充与修正"，不承担伏笔准入（准入靠 Planner 声明 + Critic 复核）。
 *
 * @author HL
 */
@Service
public class ExtractionAgent {

    /** 人物状态抽取结果 */
    public record ExtractionResult(List<CharacterStateSnapshot> snapshots, long tokens) {
    }

    private final ChatClient chatClient;
    private final BeanOutputConverter<List<CharacterStateSnapshot>> converter =
            new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
            });

    public ExtractionAgent(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * 章节摘要滚动：把新章节关键情节合并进既有滚动摘要，输出更新后的完整摘要（<=2000 字）
     *
     * @param existingDigest 既有滚动摘要（可空 = 首章）
     * @param chapterTitle   章节标题（可空）
     * @param chapterContent 新章节正文（已剥离内联标记）
     */
    public String rollUpDigest(String existingDigest, String chapterTitle, String chapterContent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                你是小说编辑部助理。请将新章节的关键情节合并进既有摘要，输出更新后的滚动前情摘要。

                【摘要要求】
                1. 覆盖主要事件与因果、人物关系变化、重要伏笔的埋设与收束。
                2. 按时间顺序组织，总长度控制在 2000 字以内。
                3. 只输出摘要正文，不要任何解释、标题或前缀。
                """);
        if (existingDigest != null && !existingDigest.isBlank()) {
            prompt.append("\n【既有滚动摘要】\n").append(existingDigest).append("\n");
        }
        prompt.append("\n【新章节】").append(chapterTitle == null ? "" : "《" + chapterTitle + "》").append("\n")
                .append(chapterContent);
        return Objects.requireNonNullElse(
                chatClient.prompt()
                        .system("你是小说编辑部助理，负责维护精准、克制的前情摘要。")
                        .user(prompt.toString())
                        .call()
                        .content(),
                "");
    }

    /**
     * 从章节正文抽取出场人物的动态状态快照
     *
     * @param chapterContent     章节正文（已剥离内联标记）
     * @param characterListText 角色清单文本（"角色ID: x, 角色名: y" 每行一个）
     */
    public ExtractionResult extractStates(String chapterContent, String characterListText) {
        String userPrompt = "请从以下章节正文中抽取每个出场人物的最新动态状态。\n\n"
                + "【角色清单（characterId 从中选择，未出场者不要输出）】\n"
                + Objects.requireNonNullElse(characterListText, "（未知，若无法确定角色ID则跳过该角色）")
                + "\n\n【章节正文】\n" + chapterContent
                + "\n\n" + converter.getFormat();

        ChatResponse response = Objects.requireNonNull(
                chatClient.prompt()
                        .system("""
                                你是小说编辑部的人物状态管理员。从正文中抽取人物在章节结束时的状态快照。
                                只依据正文明确出现的信息，禁止推断正文未提及的内容；
                                某项信息正文未提及则该字段填空字符串。
                                只输出 JSON。
                                """)
                        .user(userPrompt)
                        .call()
                        .chatResponse(),
                "抽取响应为空");

        List<CharacterStateSnapshot> snapshots = converter.convert(
                Objects.requireNonNullElse(WriterAgent.extractText(response), ""));
        long tokens = WriterAgent.usageOf(response);
        return new ExtractionResult(snapshots == null ? List.of() : snapshots, tokens);
    }
}
