package com.linpj.novel.create.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.linpj.novel.create.pojo.entity.NovelSafetyLog;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 安全审计服务测试（v2.0 M4-E1/E4：留证转义防二次注入 + 审计失败不阻塞业务）
 *
 * @author HL
 */
class SafetyAuditServiceTest {

    private SafetyAuditService service;

    @BeforeAll
    static void initEntityMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                NovelSafetyLog.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new SafetyAuditService());
    }

    @Test
    void 留证记录三要素与动作() {
        org.mockito.Mockito.doReturn(true).when(service).save(any(NovelSafetyLog.class));

        service.record(7L, "PRE", "PROMPT_INJECTION", "REJECT", "reason=指令操纵");

        ArgumentCaptor<NovelSafetyLog> captor = ArgumentCaptor.forClass(NovelSafetyLog.class);
        verify(service).save(captor.capture());
        NovelSafetyLog saved = captor.getValue();
        assertEquals(7L, saved.getBookId());
        assertEquals("PRE", saved.getLayer());
        assertEquals("PROMPT_INJECTION", saved.getCategory());
        assertEquals("REJECT", saved.getAction());
        assertTrue(saved.getDetail().contains("指令操纵"));
    }

    @Test
    void 留证转义防日志二次注入() {
        // 换行伪造日志行 + HTML 敏感符号：全部转义（日志查看端不是新攻击面）
        String evil = "正常意图\nINFO fake-entry <script>alert(1)</script> 'or 1=1";
        String escaped = SafetyAuditService.escape(evil);
        assertTrue(!escaped.contains("\n"), "换行剥离，不可伪造日志行");
        assertTrue(!escaped.contains("<"), "HTML 左尖括号转义");
        assertTrue(escaped.contains("&lt;script&gt;"), "script 标签被转义为文本");
        assertTrue(escaped.contains("&#39;or"), "单引号转义");
    }

    @Test
    void 留证超长截断() {
        String longPayload = "注入载荷".repeat(300); // 1200 字符 > 500 上限
        String escaped = SafetyAuditService.escape(longPayload);
        assertTrue(escaped.length() <= SafetyAuditService.MAX_DETAIL_LENGTH + 20,
                "超长留证截断（" + escaped.length() + "）");
        assertTrue(escaped.endsWith("…(truncated)"));
    }

    @Test
    void 空与null留证归一() {
        assertNull(SafetyAuditService.escape(null));
        assertNull(SafetyAuditService.escape(""));
    }

    @Test
    void 审计失败不阻塞业务主链路() {
        doThrow(new RuntimeException("账本库不可用"))
                .when(service).save(any(NovelSafetyLog.class));

        // 审计是观测能力：写入失败绝不反噬业务（P6 约束的是校验本体，不是记账通道）
        assertDoesNotThrow(() -> service.record(7L, "PRE", "PROMPT_INJECTION", "REJECT", "reason=x"));
    }

    @Test
    void 无效字段静默不记() {
        service.record(null, null, "C", "A", "d");
        service.record(1L, null, null, null, "d");
        verify(service, org.mockito.Mockito.never()).save(any(NovelSafetyLog.class));
    }
}
