package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.config.PipelineCriticModelProperties;
import com.linpj.novel.create.config.PipelinePricingProperties;
import com.linpj.novel.create.mapper.novel.CreationStageArtifactMapper;
import com.linpj.novel.create.pojo.entity.CreationStageArtifact;
import com.linpj.novel.create.pojo.entity.NovelCostLedger;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成本账本服务测试（M5-F4：单点派生入账 / 幂等重算 / 跨模型计价 / 分组聚合）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostLedgerServiceTest {

    @Mock private CreationStageArtifactMapper artifactMapper;

    private final PipelinePricingProperties pricing = new PipelinePricingProperties();
    private final PipelineCriticModelProperties criticProps = new PipelineCriticModelProperties();
    private CostLedgerService service;

    /** 已入账行仓（runId -> rows），同时充当聚合 list 桩的数据源 */
    private final Map<Long, List<NovelCostLedger>> ledgerStore = new LinkedHashMap<>();

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelCostLedger.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                CreationStageArtifact.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new CostLedgerService(artifactMapper, pricing, criticProps, "deepseek-main"));
        ledgerStore.clear();
        doAnswer(invocation -> {
            NovelCostLedger row = invocation.getArgument(0);
            ledgerStore.computeIfAbsent(row.getRunId(), k -> new ArrayList<>()).add(row);
            return true;
        }).when(service).save(any(NovelCostLedger.class));
        doAnswer(invocation -> ledgerStore.values().stream().flatMap(List::stream)
                .collect(java.util.stream.Collectors.toList()))
                .when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        // remove：幂等重算用（真实实现依赖 baseMapper，桩为清仓语义）
        doAnswer(invocation -> {
            ledgerStore.clear();
            return true;
        }).when(service).remove(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    private CreationStageArtifact artifact(Long taskId, String stage, Integer tokens) {
        CreationStageArtifact artifact = new CreationStageArtifact();
        artifact.setTaskId(taskId);
        artifact.setStage(stage);
        artifact.setRound(1);
        artifact.setTokens(tokens);
        return artifact;
    }

    /** 直接构造账本行入仓（聚合测试的数据准备，绕过入账路径） */
    private NovelCostLedger row(Long runId, String mode, String node, String model,
                                long tokens, String cost) {
        NovelCostLedger row = new NovelCostLedger();
        row.setRunId(runId);
        row.setBookId(1L);
        row.setMode(mode);
        row.setNode(node);
        row.setModel(model);
        row.setTotalTokens(tokens);
        row.setCost(new BigDecimal(cost));
        ledgerStore.computeIfAbsent(runId, k -> new ArrayList<>()).add(row);
        return row;
    }

    // ------------------------------------------------------------------
    // 入账：单点派生（stage→Agent→model）+ 轮次折叠
    // ------------------------------------------------------------------

    @Test
    void 入账_节点折叠与模型归属() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(
                artifact(9L, "PLANNING", 3000),
                artifact(9L, "WRITING", 10000), artifact(9L, "WRITING", 8000),
                artifact(9L, "CRITIC", 2000),
                artifact(9L, "POLISHING", 5000)));
        criticProps.setModel("qwen-critic");

        service.ledgerRun(9L, "STANDARD", 1L, 10L);

        Map<String, NovelCostLedger> byNode = new LinkedHashMap<>();
        ledgerStore.get(9L).forEach(led -> byNode.put(led.getNode(), led));
        assertEquals(4, byNode.size(), "节点级折叠（轮次去重）");
        assertEquals("deepseek-main", byNode.get("WRITING").getModel(), "Writer 族走主模型");
        assertEquals("qwen-critic", byNode.get("CRITIC").getModel(), "评审族走 Critic 模型（异源配置生效）");
        assertEquals(18000L, byNode.get("WRITING").getTotalTokens(), "同节点轮次折叠求和");
        assertEquals("STANDARD", byNode.get("CRITIC").getMode());
    }

    @Test
    void 入账_异源未配置时Critic走主模型() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact(9L, "CRITIC", 100)));
        criticProps.setModel(null); // 未配置 = 现状（A1 基线）

        service.ledgerRun(9L, "STANDARD", 1L, 10L);

        assertEquals("deepseek-main", ledgerStore.get(9L).get(0).getModel());
    }

    @Test
    void 计价_千token单价换算() {
        pricing.getPerThousand().put("deepseek-main", new BigDecimal("0.002"));
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact(9L, "WRITING", 10_000)));

        service.ledgerRun(9L, "REFINED", 1L, 10L);

        assertEquals(new BigDecimal("0.020000"), ledgerStore.get(9L).get(0).getCost(),
                "10k tokens × 0.002/1k = 0.02 元");
    }

    @Test
    void 计价_未配价目记零不虚增() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact(9L, "WRITING", 10_000)));

        service.ledgerRun(9L, "REFINED", 1L, 10L);

        NovelCostLedger ledgerRow = ledgerStore.get(9L).get(0);
        assertEquals(0, BigDecimal.ZERO.compareTo(ledgerRow.getCost()), "未配价目 cost=0（token 仍如实入账）");
        assertEquals(10_000L, ledgerRow.getTotalTokens());
    }

    @Test
    void 入账_幂等重算先清旧行() {
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact(9L, "WRITING", 100)));
        service.ledgerRun(9L, "STANDARD", 1L, 10L);
        // 二次入账（同 run 重算）：remove 先清旧行再插新行
        service.ledgerRun(9L, "STANDARD", 1L, 10L);

        verify(service, org.mockito.Mockito.times(2))
                .remove(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertEquals(1, ledgerStore.get(9L).size(), "重算不重复累加（remove 后新增单行）");
    }

    @Test
    void 入账_无产物或异常静默() {
        when(artifactMapper.selectList(any())).thenReturn(List.of());
        service.ledgerRun(9L, "FAST", 1L, 10L);
        verify(service, never()).save(any(NovelCostLedger.class));

        when(artifactMapper.selectList(any())).thenThrow(new RuntimeException("库不可用"));
        assertDoesNotThrow(() -> service.ledgerRun(9L, "FAST", 1L, 10L), "账本故障不反阻任务终态");
        service.ledgerRun(null, "FAST", 1L, 10L); // 参数防御：零副作用
        assertTrue(ledgerStore.isEmpty());
    }

    // ------------------------------------------------------------------
    // 聚合（模式 × 节点 × 模型，run 去重计数）
    // ------------------------------------------------------------------

    @Test
    void 聚合_按模式节点模型分组() {
        row(9L, "STANDARD", "WRITING", "deepseek-main", 1000, "0.002");
        row(9L, "STANDARD", "CRITIC", "qwen-critic", 500, "0.006");
        row(10L, "STANDARD", "WRITING", "deepseek-main", 3000, "0.006");
        row(11L, "FAST", "WRITING", "deepseek-main", 200, "0.0004");
        pricing.getPerThousand().put("deepseek-main", new BigDecimal("0.002"));
        pricing.getPerThousand().put("qwen-critic", new BigDecimal("0.012"));

        List<CostLedgerService.CostRow> rows = service.summarizeByBook(1L);

        assertEquals(3, rows.size(), "三个分组（标准/写作、标准/评审、速写/写作）");
        CostLedgerService.CostRow standardWriting = rows.stream()
                .filter(r -> "STANDARD".equals(r.mode()) && "WRITING".equals(r.node()))
                .findFirst().orElseThrow();
        assertEquals(2, standardWriting.runs(), "runs 按 run 去重计数");
        assertEquals(4000, standardWriting.totalTokens());
        assertEquals(0, new BigDecimal("0.008").compareTo(standardWriting.cost()), "组内成本求和（金额断言用 compareTo 免 scale 干扰）");
        assertTrue(standardWriting.priced(), "价目已配");
    }
}
