package com.linpj.novel.create.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型调用录制配置（v2.0 设计 6.1：录制回放一等能力）
 *
 * - 录制内容为完整 prompt 与响应（含书稿正文），仅落本地目录（默认 .temp/，不入 git、不入库）
 * - 实验期开启；回放端按 (agent, 输入哈希) 消费 JSONL，隔离模型随机性
 * - 每行记录 model 版本与耗时：实验期锁模型版本（红线 13）依赖此记录
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.recording")
public class PipelineRecordingProperties {

    /** 录制开关（默认关：非实验期零开销零落盘） */
    private boolean enabled = false;

    /** JSONL 录制目录（相对工作目录；默认 .temp 临时区） */
    private String dir = ".temp/model-recordings";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }
}
