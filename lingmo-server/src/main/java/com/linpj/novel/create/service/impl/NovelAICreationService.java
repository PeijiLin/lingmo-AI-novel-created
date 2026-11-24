package com.linpj.novel.create.service.impl;

import com.linpj.novel.create.pojo.dto.AiChatRequest;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.BookInfoService;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.CreateBookService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

/**
 * @author HL
 */
@Service
public class NovelAICreationService {

    private final ChatClient chatClient;
    private final CharacterCardsService characterCardsService; // 注入人物卡服务

    private final PgVectorStore vectorStore;
    private final CreateBookService createBookService;

    private final BookInfoService bookInfoService;


    public NovelAICreationService(ChatClient chatClient, CharacterCardsService characterCardsService, PgVectorStore vectorStore, CreateBookService createBookService, BookInfoService bookInfoService) { // 注入服务
        this.characterCardsService = characterCardsService;
        // 构建 ChatClient，整合 RAG 和对话记忆
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.createBookService = createBookService;
        this.bookInfoService = bookInfoService;
    }

    /**
     * AI 创作主入口
     * @param aiChatRequest AI聊天请求参数
     * @return 流式返回的AI生成内容
     */
    public Flux<String> createContent(AiChatRequest aiChatRequest) {
        // 1. 获取人物卡信息
        List<CharacterCardsVo> characters = characterCardsService.getCharacterCardsByProjectId(aiChatRequest.getBookId());
        String characterInfo = buildCharacterInfo(characters);

        // 2. 构建系统提示词
        String systemPrompt = buildSystemPrompt(
                aiChatRequest.getTitle(),
                aiChatRequest.getWorldSetting(),
                characterInfo,
                aiChatRequest.getCreationType(),
                aiChatRequest.getStyle()
        );

        // 使用rag增强检索
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).filterExpression(() -> new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key("metadata.bookId"),
                        new Filter.Value(aiChatRequest.getBookId())
                )).build()).build();

        // 3. 调用 AI，改为流式返回
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(aiChatRequest.getUserInput())
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, aiChatRequest.getChapterId()))
                .advisors(retrievalAugmentationAdvisor)
                .stream()
                .content();
    }

    /**
     * 构建人物信息字符串
     * @param characters 项目中的所有角色卡
     * @return 格式化的人物信息
     */
    private String buildCharacterInfo(List<CharacterCardsVo> characters) {
        if (characters == null || characters.isEmpty()) {
            return "无角色设定信息。";
        }
        return characters.stream()
                .map(c -> String.format(
                        "角色名: %s\n描述: %s\n性格: %s\n外貌: %s\n背景: %s\n能力: %s\n弱点: %s\n目标: %s\n关系: %s\n---\n",
                        c.getName(),
                        c.getDescription(),
                        c.getPersonality(),
                        c.getAppearance(),
                        c.getBackground(),
                        c.getAbilities(),
                        c.getWeaknesses(),
                        c.getGoals(),
                        c.getRelationships() != null ? c.getRelationships().toString() : "无"
                ))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建系统提示词
     * @param title 小说标题
     * @param worldSetting 世界观
     * @param characterInfo 角色信息
     * @param creationType 创作类型
     * @param style 风格要求
     * @return 完整的系统提示词
     */
    private String buildSystemPrompt(String title, String worldSetting, String characterInfo, String creationType, String style) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的小说创作助手，正在协助创作小说《").append(title).append("》。\n\n");

        prompt.append("【世界观设定】\n").append(worldSetting).append("\n\n");

        prompt.append("【人物设定】\n").append(characterInfo).append("\n");

        if (style != null && !style.trim().isEmpty()) {
            prompt.append("【风格要求】\n请保持 ").append(style).append(" 的写作风格。\n\n");
        }

        switch (creationType) {
            case "续写":
                prompt.append("【任务】\n请基于上述信息和之前的对话历史，自然地续写小说内容。确保风格、人物性格和世界观保持一致。");
                break;
            case "对话":
                prompt.append("【任务】\n请根据角色设定，生成符合其性格和背景的对话。对话应自然流畅，推进情节发展。");
                break;
            case "情节":
                prompt.append("【任务】\n请根据世界观和人物关系，构思一个合理的情节发展或冲突点。");
                break;
            case "描写":
                prompt.append("【任务】\n请对场景、人物外貌或动作进行细致描写。");
                break;
            case "润色":
                prompt.append("【任务】\n请对提供的文本内容进行润色优化，提升文字表达质量，使语言更生动、流畅，同时保持原意不变。");
                break;
            case "灵感":
                prompt.append("【任务】\n请根据用户提供的关键词，结合小说的世界观和人物设定，激发创作灵感，构思新颖的情节或故事发展方向。");
                break;
            default:
                prompt.append("【任务】\n请根据用户输入，结合小说设定进行创作。确保内容符合小说的整体风格和逻辑。");
        }

        prompt.append("\n\n请开始创作：");

        return prompt.toString();
    }
}