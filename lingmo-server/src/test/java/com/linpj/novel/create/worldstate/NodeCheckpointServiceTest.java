package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelNodeCheckpoint;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 节点 checkpoint 服务测试（M5-F2：幂等落账 / 复用判定 / 故障静默）
 *
 * @author HL
 */
class NodeCheckpointServiceTest {

    private NodeCheckpointService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelNodeCheckpoint.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new NodeCheckpointService());
    }

    @Test
    void 节点成功落账写入完整字段() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelNodeCheckpoint.class));

        service.markSucceeded(7L, "PLANNING", "abc123", 105, null);

        ArgumentCaptor<NovelNodeCheckpoint> captor = ArgumentCaptor.forClass(NovelNodeCheckpoint.class);
        verify(service).save(captor.capture());
        assertEquals(7L, captor.getValue().getRunId());
        assertEquals("PLANNING", captor.getValue().getNodeId());
        assertEquals("SUCCEEDED", captor.getValue().getStatus());
        assertEquals("abc123", captor.getValue().getInputHash());
        assertEquals(105, captor.getValue().getTokens());
    }

    @Test
    void 同节点重复落账幂等跳过() {
        // 已成功节点再次落账（续跑后二次成功）：UNIQUE 键前置拦截，不重复入账
        doReturn(1L).when(service).count(any());

        service.markSucceeded(7L, "PLANNING", "abc123", 105, null);

        verify(service, never()).save(any(NovelNodeCheckpoint.class));
    }

    @Test
    void 无效参数与故障静默() {
        // 旁路能力：落账故障只损失续跑资格，绝不外抛阻断管道
        assertDoesNotThrow(() -> service.markSucceeded(null, "PLANNING", "h", 1, null));
        assertDoesNotThrow(() -> service.markSucceeded(7L, " ", "h", 1, null));
        doReturn(0L).when(service).count(any());
        doThrow(new RuntimeException("库不可用")).when(service).save(any(NovelNodeCheckpoint.class));
        assertDoesNotThrow(() -> service.markSucceeded(7L, "PLANNING", "h", 1, null));
    }

    @Test
    void 复用判定要求hash一致() {
        // 同输入 → 可复用；输入变更 → 必须重做（新意图不复用旧产物）
        doReturn(1L).when(service).count(any());
        assertTrue(service.isResumable(7L, "PLANNING", "abc123"));

        when(service.count(any())).thenReturn(0L);
        assertFalse(service.isResumable(7L, "PLANNING", "abc123"));
        assertFalse(service.isResumable(7L, "PLANNING", null), "无输入指纹 = 无复用语义");
        assertFalse(service.isResumable(null, "PLANNING", "abc123"));
    }

    @Test
    void 查询故障按未成功处理宁可重做() {
        // doThrow 姿势：spy 上 when-stub 会先真实调用（baseMapper null 炸出），必须用 doThrow
        doThrow(new RuntimeException("库不可用")).when(service).count(any());

        assertFalse(service.hasSucceeded(7L, "PLANNING"), "查询故障宁可重做不可误跳");
        assertFalse(service.isResumable(7L, "PLANNING", "abc123"));
        assertTrue(service.succeededNodes(7L).isEmpty());
    }

    @Test
    void 成功节点集按入账顺序() {
        NovelNodeCheckpoint first = new NovelNodeCheckpoint();
        first.setNodeId("PLANNING");
        first.setStatus("SUCCEEDED");
        NovelNodeCheckpoint second = new NovelNodeCheckpoint();
        second.setNodeId("WRITING:1");
        second.setStatus("SUCCEEDED");
        NovelNodeCheckpoint failed = new NovelNodeCheckpoint();
        failed.setNodeId("CRITIC:1");
        failed.setStatus("FAILED");
        doReturn(List.of(first, second, failed)).when(service).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        assertEquals(List.of("PLANNING", "WRITING:1"), List.copyOf(service.succeededNodes(7L)),
                "只聚 SUCCEEDED 节点（失败节点不进续跑跳过集）");
        assertTrue(service.succeededNodes(null).isEmpty());
    }
}
