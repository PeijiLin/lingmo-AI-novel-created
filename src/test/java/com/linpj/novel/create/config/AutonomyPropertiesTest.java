package com.linpj.novel.create.config;

import com.linpj.novel.create.pojo.pipeline.CreationMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自主性矩阵配置测试（自主性改造 R0：默认关闭红线 / 三维矩阵解析 / 快照分组自变量）
 *
 * @author HL
 */
class AutonomyPropertiesTest {

    @Test
    void 默认总闸关闭且矩阵即设计矩阵() {
        AutonomyProperties props = new AutonomyProperties();
        assertFalse(props.isEnabled(), "默认 enabled=false = 逐字节现状（红线三）");

        // 低档模式恒 L1（控成本）：FAST/STANDARD 三节点全 L1
        for (CreationMode mode : List.of(CreationMode.FAST, CreationMode.STANDARD)) {
            for (String node : List.of(AutonomyProperties.NODE_PLANNER,
                    AutonomyProperties.NODE_WRITER, AutonomyProperties.NODE_CRITIC)) {
                assertEquals(AutonomyProperties.LEVEL_L1, props.resolve(mode, node).getLevel(),
                        mode + "/" + node + " 默认 L1");
            }
        }
        // 高档模式三节点开 L2（方案 §4 设计矩阵；Critic maxSteps=8 为修正案纪律 1 校准值）
        for (CreationMode mode : List.of(CreationMode.REFINED, CreationMode.PRECISE)) {
            assertEquals(AutonomyProperties.LEVEL_L2, props.resolve(mode, AutonomyProperties.NODE_PLANNER).getLevel());
            assertEquals(4, props.resolve(mode, AutonomyProperties.NODE_PLANNER).getMaxSteps());
            assertEquals(AutonomyProperties.LEVEL_L2, props.resolve(mode, AutonomyProperties.NODE_WRITER).getLevel());
            assertEquals(5, props.resolve(mode, AutonomyProperties.NODE_WRITER).getMaxSteps());
            assertEquals(AutonomyProperties.LEVEL_L2, props.resolve(mode, AutonomyProperties.NODE_CRITIC).getLevel());
            assertEquals(8, props.resolve(mode, AutonomyProperties.NODE_CRITIC).getMaxSteps());
            assertEquals(60_000L, props.resolve(mode, AutonomyProperties.NODE_CRITIC).getTokenBudget());
        }
        // 高档模式其余节点（如未纳入矩阵的节点）兜底 L1；L1 节点围栏恒 0
        assertEquals(0, props.resolve(CreationMode.FAST, AutonomyProperties.NODE_CRITIC).getMaxSteps());
        assertEquals(0L, props.resolve(CreationMode.FAST, AutonomyProperties.NODE_CRITIC).getTokenBudget());
    }

    @Test
    void 未知模式或节点兜底L1安全默认() {
        AutonomyProperties props = new AutonomyProperties();
        assertEquals(AutonomyProperties.LEVEL_L1, props.resolve(null, AutonomyProperties.NODE_CRITIC).getLevel());
        assertEquals(AutonomyProperties.LEVEL_L1, props.resolve(CreationMode.REFINED, null).getLevel());
        assertEquals(AutonomyProperties.LEVEL_L1, props.resolve(CreationMode.REFINED, "READER").getLevel(),
                "polisher/readerSim 等保持 L1，矩阵不含即兑底");
        assertEquals(AutonomyProperties.LEVEL_L1, props.resolve(null, null).getLevel());
    }

    @Test
    void isL2受总闸控制() {
        AutonomyProperties props = new AutonomyProperties();
        assertFalse(props.isL2(CreationMode.REFINED, AutonomyProperties.NODE_CRITIC),
                "矩阵配置 L2 但总闸关闭 → 现状形态（红线三）");
        props.setEnabled(true);
        assertTrue(props.isL2(CreationMode.REFINED, AutonomyProperties.NODE_CRITIC));
        assertFalse(props.isL2(CreationMode.FAST, AutonomyProperties.NODE_CRITIC));
        assertFalse(props.isL2(CreationMode.REFINED, "READER"));
    }

    @Test
    void resolve返回副本防热路径污染() {
        AutonomyProperties props = new AutonomyProperties();
        props.setEnabled(true);
        props.resolve(CreationMode.REFINED, AutonomyProperties.NODE_CRITIC).setMaxSteps(999);
        assertEquals(8, props.resolve(CreationMode.REFINED, AutonomyProperties.NODE_CRITIC).getMaxSteps(),
                "调用方改副本不反写配置");
    }

    @Test
    void 快照含分组自变量完整维度() {
        AutonomyProperties props = new AutonomyProperties();
        props.setEnabled(true);
        Map<String, Object> snap = props.snapshot(CreationMode.REFINED);

        assertEquals(Boolean.TRUE, snap.get("enabled"));
        assertEquals("REFINED", snap.get("mode"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> nodes = (Map<String, Map<String, Object>>) snap.get("nodes");
        assertEquals(3, nodes.size(), "仅三节点入矩阵");
        assertEquals(2, nodes.get("PLANNER").get("level"));
        assertEquals(4, nodes.get("PLANNER").get("maxSteps"));
        assertEquals(30_000L, nodes.get("PLANNER").get("tokenBudget"));
        assertEquals(8, nodes.get("CRITIC").get("maxSteps"));
        assertEquals(5, nodes.get("WRITER").get("maxSteps"), "WRITER 快照同构");
        // mode 为空：仍如实记录总闸状态（快照语义=实验分组自变量），但不携带 nodes 维度
        Map<String, Object> nullModeSnap = props.snapshot(null);
        assertEquals(Boolean.TRUE, nullModeSnap.get("enabled"), "总闸状态与 mode 无关，如实记录");
        assertFalse(nullModeSnap.containsKey("nodes"), "mode 缺失时不输出节点维度");
    }

    @Test
    void 矩阵整体替换支持yaml绑定覆盖() {
        AutonomyProperties props = new AutonomyProperties();
        Map<String, Map<String, AutonomyProperties.NodeSetting>> custom = new LinkedHashMap<>();
        Map<String, AutonomyProperties.NodeSetting> nodes = new LinkedHashMap<>();
        AutonomyProperties.NodeSetting setting = new AutonomyProperties.NodeSetting();
        setting.setLevel(AutonomyProperties.LEVEL_L2);
        setting.setMaxSteps(3);
        setting.setTokenBudget(10_000L);
        nodes.put(AutonomyProperties.NODE_CRITIC, setting);
        custom.put(CreationMode.PRECISE.name(), nodes);
        props.setMatrix(custom);
        props.setEnabled(true);

        assertEquals(AutonomyProperties.LEVEL_L2,
                props.resolve(CreationMode.PRECISE, AutonomyProperties.NODE_CRITIC).getLevel());
        assertEquals(3, props.resolve(CreationMode.PRECISE, AutonomyProperties.NODE_CRITIC).getMaxSteps());
        // 替换后未包含的模式/节点走兜底 L1
        assertFalse(props.isL2(CreationMode.REFINED, AutonomyProperties.NODE_CRITIC));
        assertFalse(props.isL2(CreationMode.PRECISE, AutonomyProperties.NODE_WRITER));
    }

    @Test
    void 请求级覆盖优先级与总闸约束() {
        AutonomyProperties props = new AutonomyProperties();
        props.setEnabled(true);

        // 覆盖开 L2：低档模式（矩阵 L1）被请求覆盖拉起——L4 定制档语义
        assertTrue(props.isL2(CreationMode.FAST, AutonomyProperties.NODE_CRITIC,
                Map.of("CRITIC", "L2")), "覆盖 > 矩阵");
        // 覆盖压 L1：高档模式（矩阵 L2）被请求覆盖关闭——成本旋钮
        assertFalse(props.isL2(CreationMode.REFINED, AutonomyProperties.NODE_CRITIC,
                Map.of("CRITIC", "L1")));
        // 大小写宽容 + 未知值忽略（用矩阵为 L1 的节点：未知值不得意外拉起 L2）
        assertTrue(props.isL2(CreationMode.REFINED, AutonomyProperties.NODE_WRITER,
                Map.of("writer", "l2")));
        assertFalse(props.isL2(CreationMode.FAST, AutonomyProperties.NODE_CRITIC,
                Map.of("CRITIC", "L3")), "未知值忽略——防拼写错误改变实验分组");
        assertFalse(props.isL2(CreationMode.REFINED, "READER",
                Map.of("READER", "L2")), "未知节点忽略（polisher 等保持 L1）");

        // 总闸硬约束：enabled=false 时覆盖不可开 L2（围栏先于自主性红线）
        props.setEnabled(false);
        assertFalse(props.isL2(CreationMode.FAST, AutonomyProperties.NODE_CRITIC,
                Map.of("CRITIC", "L2")));
    }

    @Test
    void 快照含覆盖生效等级与覆盖原文() {
        AutonomyProperties props = new AutonomyProperties();
        props.setEnabled(true);
        Map<String, Object> snap = props.snapshot(CreationMode.FAST,
                Map.of("CRITIC", "L2"));

        assertEquals("FAST", snap.get("mode"));
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) snap.get("overrides");
        assertEquals("L2", overrides.get("CRITIC"), "覆盖原文入快照（分组标签记请求自变量）");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> nodes = (Map<String, Map<String, Object>>) snap.get("nodes");
        assertEquals(2, nodes.get("CRITIC").get("level"), "快照记覆盖后生效等级（真实自变量）");
        assertEquals(1, nodes.get("PLANNER").get("level"), "未覆盖节点走矩阵默认");
    }
}
