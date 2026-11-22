package com.linpj.novel.create.agent;

import com.linpj.novel.create.config.PipelineCriticModelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Critic 异源模型路由测试（B2-3：配置回退语义——未配置主模型、配置后独立构建）
 *
 * @author HL
 */
class CriticModelRoutingTest {

    private final ChatModel primary = mock(ChatModel.class);

    @Test
    void 未配置专用模型_回退主模型() {
        PipelineCriticModelProperties properties = new PipelineCriticModelProperties(); // model = null

        ChatModel resolved = CriticAgent.resolveCriticModel(primary, properties, "key", "https://gw");

        assertSame(primary, resolved, "未配置 = 现状行为（与 Writer 同源，消融基线 A1 语义）");
    }

    @Test
    void 空白模型名_同样回退() {
        PipelineCriticModelProperties properties = new PipelineCriticModelProperties();
        properties.setModel("   ");

        assertSame(primary, CriticAgent.resolveCriticModel(primary, properties, "key", "https://gw"));
    }

    @Test
    void 配置专用模型_构建异源实例() {
        PipelineCriticModelProperties properties = new PipelineCriticModelProperties();
        properties.setModel("qwen3.8-max-0902");
        // 异源 key/base-url 未配置：继承主配置（同一百炼 MaaS 网关只换模型名）

        ChatModel resolved = CriticAgent.resolveCriticModel(primary, properties, "main-key", "https://main-gw");

        assertNotNull(resolved);
        assertEquals(OpenAiChatModelShim.modelOf(resolved), "qwen3.8-max-0902",
                "异源实例默认模型 = 配置的 Critic 专用模型");
    }

    @Test
    void 配置null_语义等同未配置() {
        assertSame(primary, CriticAgent.resolveCriticModel(primary, null, "key", "https://gw"),
                "配置对象缺位（离线装配）= 回退主模型");
    }

    /** 桥接访问异源实例的默认模型名（避免测试直接依赖 OpenAiChatModel 内部结构） */
    static final class OpenAiChatModelShim {
        static String modelOf(ChatModel model) {
            return ((org.springframework.ai.openai.OpenAiChatModel) model)
                    .getDefaultOptions().getModel();
        }
    }
}
