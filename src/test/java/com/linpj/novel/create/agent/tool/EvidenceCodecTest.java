package com.linpj.novel.create.agent.tool;

import com.linpj.novel.create.pojo.entity.NovelToolReceipt;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * evidenceId 编解码测试（自主性改造 R1b：§11.1-M1 引文协议核心）
 *
 * 锁定：注入/重放同一纯函数（序列逐字节一致）、确定性校验四维（格式/存在/界内/文本一致）。
 *
 * @author HL
 */
class EvidenceCodecTest {

    @Test
    void 注入生成证据指针() {
        String wrapped = EvidenceCodec.wrap(55L, List.of(
                Map.of("text", "角色左臂受伤"),
                Map.of("text", "第三天清晨")));

        assertTrue(wrapped.contains("\"" + EvidenceCodec.EVIDENCE_ID_FIELD + "\":\"ev:55#1\""));
        assertTrue(wrapped.contains("\"" + EvidenceCodec.EVIDENCE_ID_FIELD + "\":\"ev:55#2\""));
        assertTrue(wrapped.contains("角色左臂受伤"));
    }

    @Test
    void 重放重打与注入逐字节一致() {
        // 回执存原文（无 id），重放 rewrap 与首放 wrap 产出必须一致——kill-resume 序列不变式
        List<Map<String, Object>> items = List.of(
                Map.of("fact", "受伤"),
                Map.of("fact", "康复"));

        String first = EvidenceCodec.wrap(77L, items);
        String receiptPayload = com.linpj.novel.create.utils.JsonUtil.toJson(items); // 回执存原文
        String replay = EvidenceCodec.rewrap(77L, receiptPayload);

        assertEquals(first, replay, "纯函数：wrap(注入) == rewrap(重放)，序列逐字节一致");
    }

    @Test
    void 非结构化回执原样透传() {
        assertEquals("工具执行失败：超时", EvidenceCodec.rewrap(9L, "工具执行失败：超时"));
        assertEquals("拒绝执行：工具 foo 不在白名单",
                EvidenceCodec.rewrap(9L, "拒绝执行：工具 foo 不在白名单"));
        String wrappedPlain = EvidenceCodec.wrap(9L, "纯文本结果");
        assertEquals("纯文本结果", wrappedPlain, "非 List 结果 wrap 透传（错误提示不是证据）");
    }

    @Test
    void 字符串条目包装为单字段条目() {
        // summary.get 返回 List<String>（章摘要文本）——条目包装为 {"text": ..., evidenceId}
        String wrapped = EvidenceCodec.wrap(3L, List.of("第一章摘要", "第二章摘要"));

        assertTrue(wrapped.contains("第一章摘要"));
        assertTrue(wrapped.contains("ev:3#1"));
        assertTrue(wrapped.contains("ev:3#2"));
    }

    @Test
    void 解析合法与非法地址() {
        assertArrayEquals(new long[]{55L, 2L}, EvidenceCodec.parse("ev:55#2"));
        assertNull(EvidenceCodec.parse("ev:55"));
        assertNull(EvidenceCodec.parse("55#2"));
        assertNull(EvidenceCodec.parse("ev:0#1"), "receiptId 必须 >0");
        assertNull(EvidenceCodec.parse("ev:55#0"), "序号从 1 起");
        assertNull(EvidenceCodec.parse(null));
        assertNull(EvidenceCodec.parse("ev:-1#1"));
    }

    private NovelToolReceipt receipt(long id, String result) {
        NovelToolReceipt receipt = new NovelToolReceipt();
        receipt.setId(id);
        receipt.setResult(result);
        return receipt;
    }

    @Test
    void 确定性校验四维() {
        String items = com.linpj.novel.create.utils.JsonUtil.toJson(List.of(
                Map.of("text", "角色左臂在第12章受伤")));
        NovelToolReceipt ok = receipt(55L, items);

        // 存在+界内+文本一致 → 通过
        assertTrue(EvidenceCodec.verify("ev:55#1", "左臂在第12章受伤", ok).ok(),
                "引文是条目子串 → 一致（归一化互含）");
        assertTrue(EvidenceCodec.verify("ev:55#1", "角色左臂在第12章受伤。", ok).ok());

        // 格式非法
        assertFalse(EvidenceCodec.verify("bad-id", "x", ok).ok());
        // 回执不存在
        assertFalse(EvidenceCodec.verify("ev:99#1", "x", ok).ok());
        assertFalse(EvidenceCodec.verify("ev:55#1", "x", null).ok());
        // 序号越界
        assertFalse(EvidenceCodec.verify("ev:55#5", "x", ok).ok());
        // 文本不一致（幻觉引文）
        assertFalse(EvidenceCodec.verify("ev:55#1", "角色右臂受伤", ok).ok(),
                "引文与条目无交集 → 幻觉判定（防报假证）");
        // 非结构化回执（无条目可回验）
        assertFalse(EvidenceCodec.verify("ev:55#1", "x", receipt(55L, "纯文本错误")).ok());
    }
}
