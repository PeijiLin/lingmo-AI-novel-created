package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelCitationLink;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 引文绑定账本服务测试（自主性改造 R1b：校验即落账 / 静默容错 / 幂等 / 有效率统计）
 *
 * @author HL
 */
class CitationLinkServiceTest {

    private CitationLinkService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelCitationLink.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new CitationLinkService());
    }

    @Test
    void 绑定落账完整字段() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelCitationLink.class));

        service.record(7L, "CRITIC:1", 3, "角色左臂受伤", "ev:55#1", 55L, 1, true, null);

        ArgumentCaptor<NovelCitationLink> captor = ArgumentCaptor.forClass(NovelCitationLink.class);
        verify(service).save(captor.capture());
        assertEquals("CRITIC:1", captor.getValue().getNodeId());
        assertEquals(3, captor.getValue().getStepNo());
        assertEquals("ev:55#1", captor.getValue().getEvidenceId());
        assertEquals(55L, captor.getValue().getReceiptId());
        assertEquals(1, captor.getValue().getItemIndex());
        assertEquals(Boolean.TRUE, captor.getValue().getOk());
    }

    @Test
    void 同键重复落账幂等跳过() {
        doReturn(1L).when(service).count(any());

        assertNull(service.record(7L, "CRITIC:1", 3, "x", "ev:55#1", 55L, 1, true, null));
        verify(service, never()).save(any(NovelCitationLink.class));
    }

    @Test
    void 无效参数与故障静默容错() {
        // 引文账是审计面：落账失败不阻断收口流程（判定以内存校验结果为准）
        assertNull(service.record(null, "CRITIC:1", 3, "x", "ev:1#1", 1, 1, true, null));
        assertNull(service.record(7L, " ", 3, "x", "ev:1#1", 1, 1, true, null));
        assertNull(service.record(7L, "CRITIC:1", 3, "x", null, 1, 1, true, null));
        assertNull(service.record(7L, "CRITIC:1", 3, "x", "ev:1#0", 1, 0, true, null), "序号从 1 起");

        doReturn(0L).when(service).count(any());
        doThrow(new RuntimeException("库不可用")).when(service).save(any(NovelCitationLink.class));
        assertDoesNotThrow(() ->
                service.record(7L, "CRITIC:1", 3, "x", "ev:1#1", 1, 1, false, "文本不一致"));
    }
}
