package com.linpj.novel.create.config;

import com.linpj.novel.create.pojo.pipeline.CreationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自主性矩阵配置（自主性改造 R0：四档模式 × 节点 × 等级的三维裁剪维度）
 *
 * <p>设计（docs/AGENTIC_RUNTIME_PLAN.md §4 + 修正案 §11.1-M2）：
 * <ul>
 *   <li><b>独立配置类，不并入 PipelineFeatures</b>——后者是一维 boolean + 请求级覆盖，
 *       自主性是 mode × node × level 三维矩阵，硬塞 boolean 会扭曲；请求级覆盖 R2 接入</li>
 *   <li><b>enabled 总闸默认 false = 逐字节现状</b>（红线三：L2 必可降级 L1——旧调用
 *       路径全程保留，flag 切换）。任务快照（creation_task.params.autonomy）仅在
 *       enabled 时落盘：OFF 状态下 params 逐字节不变，基线 diff 安全</li>
 *   <li><b>E5 分组自变量</b>：快照随任务落库（enabled + 节点级 level/maxSteps/tokenBudget），
 *       实验数据行自带分组标签，不依赖事后回忆</li>
 * </ul>
 *
 * <p>等级定义：L0=纯代码节点（RuleChecker/编排器，不适用本矩阵）；L1=单次调用
 * （现状全部节点形态）；L2=有界 Agent（AgentRuntime 显式步骤循环——围栏内模型
 * 持有步骤决策）。
 *
 * <p>默认矩阵即 §4 设计矩阵：低档模式（FAST/STANDARD）恒 L1 控成本；高档
 * （REFINED/PRECISE）三节点开 L2。Critic maxSteps=8 为修正案纪律 1 校准值
 * （原方案 6 偏紧，主约束靠 tokenBudget；默认值待 S10 成本实验校准）。
 *
 * @author HL
 */
@Component
@ConfigurationProperties(prefix = "novel.pipeline.autonomy")
public class AutonomyProperties {

    /** L0 纯代码（编排层/规则器；本矩阵不管理 L0 节点——它们没有模型调用） */
    public static final int LEVEL_L0 = 0;

    /** L1 单次调用（现状形态：一次 ChatClient 调用即返回） */
    public static final int LEVEL_L1 = 1;

    /** L2 有界 Agent（AgentRuntime 步骤循环：围栏内模型持有步骤决策） */
    public static final int LEVEL_L2 = 2;

    /** 矩阵节点键：Planner（大纲规划） */
    public static final String NODE_PLANNER = "PLANNER";

    /** 矩阵节点键：Writer（正文生成） */
    public static final String NODE_WRITER = "WRITER";

    /** 矩阵节点键：Critic（一致性审查） */
    public static final String NODE_CRITIC = "CRITIC";

    private boolean enabled = false;

    /** mode → node → 档位（代码默认值即设计矩阵；yaml 覆盖或替换） */
    private Map<String, Map<String, NodeSetting>> matrix = defaultMatrix();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Map<String, NodeSetting>> getMatrix() {
        return matrix;
    }

    public void setMatrix(Map<String, Map<String, NodeSetting>> matrix) {
        this.matrix = matrix;
    }

    /**
     * 解析档位（副本返回——防热路径调用方误改配置）。
     * 未知模式/节点兜底 L1（安全默认：现状单次调用形态）。
     */
    public NodeSetting resolve(CreationMode mode, String node) {
        if (mode == null || node == null || matrix == null) {
            return new NodeSetting();
        }
        Map<String, NodeSetting> nodes = matrix.get(mode.name());
        if (nodes == null) {
            return new NodeSetting();
        }
        NodeSetting setting = nodes.get(node);
        return setting == null ? new NodeSetting() : setting.copy();
    }

    /** 节点是否以 L2 有界 Agent 形态运行（总闸关闭恒 false——现状红线） */
    public boolean isL2(CreationMode mode, String node) {
        return enabled && resolve(mode, node).getLevel() == LEVEL_L2;
    }

    /**
     * 请求级覆盖判定（R2b，L4 定制档）：优先级 覆盖 > 矩阵（矩阵级总闸仍硬约束——
     * enabled=false 时覆盖不可开 L2，防绕围栏）。
     *
     * @param overrides 节点→"L1"/"L2"（大小写宽容；未知键/值忽略——PipelineFeatures.merged 同款纪律）
     */
    public boolean isL2(CreationMode mode, String node, Map<String, String> overrides) {
        if (overriddenLevel(node, overrides) == LEVEL_L2) {
            return enabled; // 覆盖开 L2 仍受总闸约束（围栏先于自主性红线）
        }
        if (overriddenLevel(node, overrides) == LEVEL_L1) {
            return false; // 覆盖压 L1：高档模式也可关某节点自主性（成本旋钮）
        }
        return isL2(mode, node);
    }

    /** 覆盖值解析（仅三 agent 节点接受覆盖——其余节点无 L2 实现，覆盖无效；未知值返回 -1 = 无覆盖语义） */
    private int overriddenLevel(String node, Map<String, String> overrides) {
        if (overrides == null || node == null
                || !node.equals(NODE_PLANNER) && !node.equals(NODE_WRITER) && !node.equals(NODE_CRITIC)) {
            return -1;
        }
        String value = overrides.get(node);
        if (value == null) {
            return -1;
        }
        return switch (value.trim().toUpperCase()) {
            case "L1" -> LEVEL_L1;
            case "L2" -> LEVEL_L2;
            default -> -1; // 未知值忽略（防拼写错误悄悄改变实验分组）
        };
    }

    /**
     * 任务级快照（E5 分组自变量；随 creation_task.params 落库）。
     * 仅 enabled 时被调用落盘——OFF 状态 params 逐字节现状。
     * R2b：快照含请求级覆盖结果（覆盖后的生效等级——分组标签记真实自变量）。
     */
    public Map<String, Object> snapshot(CreationMode mode, Map<String, String> overrides) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("enabled", enabled);
        if (mode != null) {
            snap.put("mode", mode.name());
            if (overrides != null && !overrides.isEmpty()) {
                snap.put("overrides", new LinkedHashMap<>(overrides));
            }
            Map<String, Object> nodes = new LinkedHashMap<>();
            for (String node : new String[]{NODE_PLANNER, NODE_WRITER, NODE_CRITIC}) {
                NodeSetting setting = resolve(mode, node);
                Map<String, Object> nodeSnap = new LinkedHashMap<>();
                nodeSnap.put("level", isL2(mode, node, overrides) ? LEVEL_L2 : LEVEL_L1);
                nodeSnap.put("maxSteps", setting.getMaxSteps());
                nodeSnap.put("tokenBudget", setting.getTokenBudget());
                nodes.put(node, nodeSnap);
            }
            snap.put("nodes", nodes);
        }
        return snap;
    }

    /** 兼容保留（无覆盖路径） */
    public Map<String, Object> snapshot(CreationMode mode) {
        return snapshot(mode, null);
    }

    /** 时间预算与步数围栏（§4 设计矩阵默认值；数值待 S10 成本实验校准） */
    public static class NodeSetting {

        /** 自主性等级：1=单次调用（现状） 2=有界 Agent */
        private int level = LEVEL_L1;

        /** 内循环步数上限（L1 恒 0；1 步 = 1 次模型调用，解析失败不计步） */
        private int maxSteps = 0;

        /** 内循环 token 预算（L1 恒 0；主约束维度——修正案纪律 1） */
        private long tokenBudget = 0;

        /** 是否开放 ask_human 人工卡点（R3：默认 false——围栏先于自主性；yaml 按模式×节点开启） */
        private boolean allowAskHuman = false;

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public long getTokenBudget() {
            return tokenBudget;
        }

        public void setTokenBudget(long tokenBudget) {
            this.tokenBudget = tokenBudget;
        }

        public boolean isAllowAskHuman() {
            return allowAskHuman;
        }

        public void setAllowAskHuman(boolean allowAskHuman) {
            this.allowAskHuman = allowAskHuman;
        }

        NodeSetting copy() {
            NodeSetting copy = new NodeSetting();
            copy.level = this.level;
            copy.maxSteps = this.maxSteps;
            copy.tokenBudget = this.tokenBudget;
            copy.allowAskHuman = this.allowAskHuman;
            return copy;
        }
    }

    /** 代码默认矩阵 = §4 设计矩阵（yaml 同构键可覆盖任意单元） */
    static Map<String, Map<String, NodeSetting>> defaultMatrix() {
        Map<String, Map<String, NodeSetting>> matrix = new LinkedHashMap<>();
        for (CreationMode mode : new CreationMode[]{CreationMode.FAST, CreationMode.STANDARD}) {
            Map<String, NodeSetting> nodes = new LinkedHashMap<>();
            nodes.put(NODE_PLANNER, new NodeSetting());
            nodes.put(NODE_WRITER, new NodeSetting());
            nodes.put(NODE_CRITIC, new NodeSetting());
            matrix.put(mode.name(), nodes);
        }
        for (CreationMode mode : new CreationMode[]{CreationMode.REFINED, CreationMode.PRECISE}) {
            Map<String, NodeSetting> nodes = new LinkedHashMap<>();
            nodes.put(NODE_PLANNER, setting(LEVEL_L2, 4, 30_000));
            nodes.put(NODE_WRITER, setting(LEVEL_L2, 5, 60_000));
            nodes.put(NODE_CRITIC, setting(LEVEL_L2, 8, 60_000));
            matrix.put(mode.name(), nodes);
        }
        return matrix;
    }

    private static NodeSetting setting(int level, int maxSteps, long tokenBudget) {
        NodeSetting nodeSetting = new NodeSetting();
        nodeSetting.setLevel(level);
        nodeSetting.setMaxSteps(maxSteps);
        nodeSetting.setTokenBudget(tokenBudget);
        return nodeSetting;
    }
}
