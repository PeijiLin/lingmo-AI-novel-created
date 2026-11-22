package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.config.PipelineCriticModelProperties;
import com.linpj.novel.create.config.PipelinePricingProperties;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.mapper.novel.NovelCostLedgerMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.entity.NovelCostLedger;
import com.linpj.novel.create.pojo.pipeline.StageCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本账本服务（M5-F4：token/成本核算与导出——论文 §10.7 性能成本实验与 §11.1 相对成本的数据源）
 *
 * 入账设计（单点派生，幂等可重算）：
 * - 时机：任务终态（成功/失败均入账——已烧掉的 token 不因任务失败消失），编排器单点调用
 * - 溯源：从 creation_stage_artifact 派生，stage → Agent → model 映射确定：
 *     PLANNING/WRITING/POLISHING → 主模型；CRITIC（含成对比较/润色复核轮）→ Critic 模型（异源配置生效）
 * - 粒度：每个 run 每个节点一行（轮次/变体折叠求和），UNIQUE(run_id, node) 幂等：
 *   重算先清旧行再插新行，价目变更后可整书重算且不重复累加
 *
 * 计价：total_tokens/1000 × 价目（novel.pipeline.pricing.per-1k.{model}）。
 * 未配价目 cost 记 0 并在导出中单列——token 如实入账，成本不虚增不漏记。
 * 实验期锁模型版本同时锁价目（红线 13 同源），历史账本不随价目变更重算。
 *
 * @author HL
 */
@Service
public class CostLedgerService extends ServiceImpl<NovelCostLedgerMapper, NovelCostLedger> {

    private static final Logger log = LoggerFactory.getLogger(CostLedgerService.class);

    private final CreationStageArtifactMapper artifactMapper;
    private final PipelinePricingProperties pricingProperties;
    private final PipelineCriticModelProperties criticModelProperties;
    private final String mainModel;

    public CostLedgerService(CreationStageArtifactMapper artifactMapper,
                             PipelinePricingProperties pricingProperties,
                             PipelineCriticModelProperties criticModelProperties,
                             @Value("${spring.ai.openai.chat.options.model:unknown}") String mainModel) {
        this.artifactMapper = artifactMapper;
        this.pricingProperties = pricingProperties;
        this.criticModelProperties = criticModelProperties;
        this.mainModel = mainModel;
    }

    // ------------------------------------------------------------------
    // 入账（单点、幂等、静默容错——账本是观测能力不是管道依赖）
    // ------------------------------------------------------------------

    /**
     * run 成本入账（任务终态调用；失败任务同样入账）。
     * 幂等：先清除该 run 既有行再插（价目变更后重算不重复累加）。
     */
    public void ledgerRun(Long runId, String mode, Long bookId, Long chapterId) {
        if (runId == null) {
            return;
        }
        try {
            List<CreationStageArtifact> artifacts = artifactMapper.selectList(
                    new LambdaQueryWrapper<CreationStageArtifact>()
                            .eq(CreationStageArtifact::getTaskId, runId));
            if (artifacts == null || artifacts.isEmpty()) {
                return;
            }
            // 节点折叠：stage → (tokens 合计)。token=null 的行（如 ADOPTED 轮次标记行）按 0 计
            Map<String, Long> tokensByNode = new LinkedHashMap<>();
            for (CreationStageArtifact artifact : artifacts) {
                if (artifact == null || artifact.getStage() == null) {
                    continue;
                }
                long tokens = artifact.getTokens() == null ? 0L : artifact.getTokens();
                tokensByNode.merge(artifact.getStage(), tokens, Long::sum);
            }
            this.remove(new LambdaQueryWrapper<NovelCostLedger>()
                    .eq(NovelCostLedger::getRunId, runId));
            for (Map.Entry<String, Long> entry : tokensByNode.entrySet()) {
                String node = entry.getKey();
                String model = modelOfNode(node);
                NovelCostLedger row = new NovelCostLedger();
                row.setRunId(runId);
                row.setBookId(bookId);
                row.setChapterId(chapterId);
                row.setMode(mode);
                row.setNode(node);
                row.setModel(model);
                row.setTotalTokens(entry.getValue());
                row.setCost(costOf(entry.getValue(), model));
                this.save(row);
            }
        } catch (Exception e) {
            // 静默容错：账本故障不反阻任务终态上报
            log.warn("成本账本入账失败（不影响任务）: runId={}, error={}", runId, e.getMessage());
        }
    }

    /** 节点 → 模型（stage→Agent 映射确定：评审族走 Critic 模型，其余走主模型） */
    private String modelOfNode(String node) {
        if (StageCode.CRITIC.name().equals(node)) {
            if (criticModelProperties != null
                    && criticModelProperties.getModel() != null
                    && !criticModelProperties.getModel().isBlank()) {
                return criticModelProperties.getModel();
            }
        }
        return mainModel;
    }

    /** 计价：tokens/1000 × 单价（未配价目记 0——导出报告中"未计价 token"单列不虚增） */
    private BigDecimal costOf(Long tokens, String model) {
        BigDecimal price = pricingProperties == null ? null : pricingProperties.priceOf(model);
        if (price == null || tokens == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .multiply(price)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 聚合（导出口径：模式 × 节点 × 模型——论文 11.1 成本模型与 10.7 成本实验的表）
    // ------------------------------------------------------------------

    /** 聚合行（导出 DTO；记录数 = 分组内 run 数） */
    public record CostRow(String mode, String node, String model, long runs,
                          long totalTokens, BigDecimal cost, boolean priced) {
    }

    /** 书级成本聚合（模式 × 节点 × 模型分组，runs 按 run 去重计数） */
    public List<CostRow> summarizeByBook(Long bookId) {
        List<NovelCostLedger> rows = this.list(new LambdaQueryWrapper<NovelCostLedger>()
                .eq(NovelCostLedger::getBookId, bookId));
        // group: mode|node|model -> [runs, tokens, costSum]
        Map<String, CostRow> grouped = new LinkedHashMap<>();
        for (NovelCostLedger row : rows) {
            if (row == null || row.getNode() == null) {
                continue;
            }
            String key = nullToEmpty(row.getMode()) + "|" + row.getNode() + "|" + nullToEmpty(row.getModel());
            BigDecimal price = pricingProperties == null ? null : pricingProperties.priceOf(row.getModel());
            boolean priced = price != null;
            grouped.merge(key,
                    new CostRow(nullToEmpty(row.getMode()), row.getNode(), nullToEmpty(row.getModel()),
                            1, row.getTotalTokens() == null ? 0 : row.getTotalTokens(),
                            row.getCost() == null ? BigDecimal.ZERO : row.getCost(), priced),
                    (a, b) -> new CostRow(a.mode(), a.node(), a.model(), a.runs() + b.runs(),
                            a.totalTokens() + b.totalTokens(),
                            a.cost().add(b.cost()), a.priced() || b.priced()));
        }
        return new ArrayList<>(grouped.values());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
