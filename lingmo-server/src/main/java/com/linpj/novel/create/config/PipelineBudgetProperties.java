package com.linpj.novel.create.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 预算守卫配置（v2.0 设计 5.4 三重预算 / M5-F1：书级 → 章级 → 轮级显式链）
 *
 * <p>三级链（评审 P1-4 的显式统一表述，节点职责见 CreationPipelineService）：
 * <ol>
 *   <li><b>书级</b>（跨任务持久预算）：maxTokens 硬上限，超限**拒绝启动新章生成**
 *       （管道入口校验，任务直接失败转人工）；80% 阈值告警留痕。</li>
 *   <li><b>章级</b>（单次任务会话预算）：token + 墙钟时延两维生效（成本维待 F4 价目配置
 *       接入，当前无单价即恒 0 不触发——见 {@link ChapterBudget} javadoc）；任一维
 *       触达档位阈值即进入对应降级档。</li>
 *   <li><b>轮级</b>：修订累计 token 超首写 40% 强制出循环（M3-D3 已落地，
 *       RewritingLoop 内 REVISION_BUDGET_RATIO）。</li>
 * </ol>
 *
 * <p><b>不可配置关闭（E6 硬编码契约，TC-30）</b>：本类只允许配置预算<emph>数值</em>；
 * 降级<emph>语义</em>（各档位做什么、比例阈值 0.8/0.9/1.0/1.2、安全与硬校验永不为
 * 预算让步）全部为代码常量——{@code PipelineFeatures} 开关域不含预算键，
 * 把阈值设成无穷大是唯一"关闭"方式且该事实可被审计。
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.budget")
public class PipelineBudgetProperties {

    private final Chapter chapter = new Chapter();
    private final Book book = new Book();

    public Chapter getChapter() {
        return chapter;
    }

    public Book getBook() {
        return book;
    }

    /** 章级预算（三维中的 token / 墙钟两维生效；成本维随 F4 价目接入） */
    public static class Chapter {

        /** 单章 token 上限（L3=100% 触顶档；默认值偏宽松，由 S10 成本实验校准） */
        private long maxTokens = 200_000;

        /** 单章墙钟上限（毫秒；默认 10 分钟——含多轮修订的整章端到端） */
        private long maxElapsedMs = 600_000;

        public long getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(long maxTokens) {
            this.maxTokens = maxTokens;
        }

        public long getMaxElapsedMs() {
            return maxElapsedMs;
        }

        public void setMaxElapsedMs(long maxElapsedMs) {
            this.maxElapsedMs = maxElapsedMs;
        }
    }

    /** 书级预算（跨任务累计：creation_task.total_tokens 按书求和） */
    public static class Book {

        /** 单书累计 token 硬上限（默认 1000 万 ≈ 50 章 × 20 万，按项目实情调整） */
        private long maxTokens = 10_000_000;

        public long getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(long maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}
