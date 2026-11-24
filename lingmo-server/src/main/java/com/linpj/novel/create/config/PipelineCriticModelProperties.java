package com.linpj.novel.create.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Critic 异源模型配置（B2-3：v2.0 8.1 评分稳定性——异源模型抑制与 Writer 同源的自我偏袒）
 *
 * - model 未配置：Critic 使用主模型（与 Writer 同源）——现状行为，消融基线 A1 语义
 * - model 配置后：Critic 独立调用该模型（如 qwen 系，与 Writer 的 deepseek 系异源）
 * - api-key / base-url 未配置时继承 spring.ai.openai.chat 主配置
 *   （同一百炼 MaaS 网关下多模型只需换 model 名）
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.models.critic")
public class PipelineCriticModelProperties {

    /** Critic 专用模型名（null/空 = 未配置，回退主模型） */
    private String model;

    /** 专用 API Key（null = 继承主配置） */
    private String apiKey;

    /** 专用 base-url（null = 继承主配置；注意不带末尾 /v1） */
    private String baseUrl;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
