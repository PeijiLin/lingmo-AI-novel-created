package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelCanonProposal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 正典提案暂存服务测试（自主性改造 R2a：内容寻址幂等 / 静默容错 / 只暂存不写入）
 *
 * @author HL
 */
class CanonProposalServiceTest {

    private CanonProposalService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelCanonProposal.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new CanonProposalService());
    }

    @Test
    void 提案暂存完整字段与PENDING状态() {
        doReturn(0L).when(service).count(any());
        doReturn(true).when(service).save(any(NovelCanonProposal.class));

        service.record(7L, "WRITING:1", 2, 33L,
                "{\"type\":\"STATE\",\"characterId\":1001,\"state\":{\"伤势\":\"左臂痊愈\"}}", null);

        ArgumentCaptor<NovelCanonProposal> captor = ArgumentCaptor.forClass(NovelCanonProposal.class);
        verify(service).save(captor.capture());
        assertEquals("WRITING:1", captor.getValue().getNodeId());
        assertEquals(2, captor.getValue().getStepNo());
        assertEquals(33L, captor.getValue().getReceiptId());
        assertEquals(CanonProposalService.STATUS_PENDING, captor.getValue().getStatus(),
                "暂存态 PENDING——提交门（R3）裁定 ADOPTED/REJECTED");
        assertEquals(64, captor.getValue().getProposalHash().length(), "哈希缺省自算（SHA-256 十六进制 64 位）");
    }

    @Test
    void 同提案重复emit内容寻址幂等() {
        doReturn(1L).when(service).count(any());

        assertNull(service.record(7L, "WRITING:1", 3, null,
                "{\"type\":\"STATE\",\"characterId\":1001,\"state\":{}}", "fixed-hash"));
        verify(service, never()).save(any(NovelCanonProposal.class));
    }

    @Test
    void 无效参数与故障静默容错() {
        // 提案账是审计面：落账失败不阻断工具执行
        assertNull(service.record(null, "WRITING:1", 1, null, "{}", null));
        assertNull(service.record(7L, " ", 1, null, "{}", null));
        assertNull(service.record(7L, "WRITING:1", 1, null, "", null));

        doReturn(0L).when(service).count(any());
        doThrow(new RuntimeException("库不可用")).when(service).save(any(NovelCanonProposal.class));
        assertDoesNotThrow(() ->
                service.record(7L, "WRITING:1", 1, null, "{\"x\":1}", "h"));
    }
}
