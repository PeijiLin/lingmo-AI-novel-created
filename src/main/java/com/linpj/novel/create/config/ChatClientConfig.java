package com.linpj.novel.create.config;


import com.alibaba.cloud.ai.memory.redis.JedisRedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配：对话记忆（Redis 仓库 + 窗口截断）统一在此构建，供 FAST 模式入口与各管道 Agent 复用
 *
 * @author HL
 */
@Configuration
public class ChatClientConfig {

    /**
     * 对话记忆：Redis 仓库 + 10 条消息窗口（各 Agent 通过 conversationId 前缀隔离记忆作用域）
     */
    @Bean
    public MessageWindowChatMemory chatMemory(JedisRedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    /**
     * 现有 FAST 模式入口使用的 ChatClient（保持原有行为，老前端零改动）
     */
    @Bean
    public ChatClient chatClientInit(ChatModel chatModel, MessageWindowChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
