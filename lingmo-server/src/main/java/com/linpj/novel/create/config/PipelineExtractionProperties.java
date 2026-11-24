package com.linpj.novel.create.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 抽取质量护栏配置（v2.0 设计 2.5 / 风险 R-16）
 *
 * - 置信度 < 阈值的抽取结果不进正典权威层（人物状态不入账，仅留日志，随后续人审队列接管）
 * - 阈值默认 0.6 为迁移期初始值：v2.0 明确"置信度阈值必须用标注集校准，不能拍脑袋定 0.8"，
 *   FX-09 金标准标注完成后（S6/评审 N7 合并标注）需重新校准此值
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.extraction")
public class PipelineExtractionProperties {

    /** 抽取置信度阈值（0.0–1.0，低于此值不入账；待金标准校准） */
    private double confidenceThreshold = 0.6;

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }
}
