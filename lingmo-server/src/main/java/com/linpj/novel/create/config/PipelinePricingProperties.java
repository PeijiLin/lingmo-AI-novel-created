package com.linpj.novel.create.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型价目表（M5-F4：novel.pipeline.pricing.per-1k.{model} = 每千 token 单价，元）
 *
 * - 键为模型名（与 JSONL.model / 账本 model 列同源）：如 deepseek-v4.1-flash-1017、qwen3.8-max-0902
 * - 未配价目的模型 cost 记 0（token 仍如实入账——报表中"已计价/未计价"分列，不虚增也不漏记）
 * - 价目只影响成本换算，不影响 token 记账：历史账本不随价目变更重算（实验期锁价目，红线 13 同源）
 *
 * dev 配置模板（application-dev.yml）：
 *   novel.pipeline.pricing.per-1k:
 *     deepseek-v4.1-flash-1017: 0.002
 *     qwen3.8-max-0902: 0.012
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.pricing")
public class PipelinePricingProperties {

    /** 模型名 → 每千 token 单价（元） */
    private Map<String, BigDecimal> perThousand = new LinkedHashMap<>();

    public Map<String, BigDecimal> getPerThousand() {
        return perThousand;
    }

    public void setPerThousand(Map<String, BigDecimal> perThousand) {
        this.perThousand = perThousand;
    }

    /** 查单价（未配置返回 null——调用方按 0 计并标注未计价） */
    public BigDecimal priceOf(String model) {
        return model == null ? null : perThousand.get(model);
    }
}
