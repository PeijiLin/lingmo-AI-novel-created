package com.linpj.novel.create.pojo.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AliveStatus 容错解析测试（B5：旧数据/脏值/缺失一律 UNKNOWN，不放大成误判）
 *
 * @author HL
 */
class AliveStatusTest {

    @Test
    void 合法枚举值解析() {
        assertEquals(AliveStatus.DEAD, AliveStatus.parse("DEAD"));
        assertEquals(AliveStatus.ALIVE, AliveStatus.parse("alive"));
        assertEquals(AliveStatus.DEAD, AliveStatus.parse("  Dead "));
    }

    @Test
    void 脏值与非字符串归UNKNOWN() {
        assertEquals(AliveStatus.UNKNOWN, AliveStatus.parse("死亡"));        // 模型漂移输出中文
        assertEquals(AliveStatus.UNKNOWN, AliveStatus.parse("alive?"));     // 带标点
        assertEquals(AliveStatus.UNKNOWN, AliveStatus.parse(""));           // 空串
        assertEquals(AliveStatus.UNKNOWN, AliveStatus.parse(null));         // 旧数据无字段
        assertEquals(AliveStatus.UNKNOWN, AliveStatus.parse(42));           // 非字符串
        assertEquals(AliveStatus.UNKNOWN, AliveStatus.parse(Boolean.TRUE)); // 非字符串
    }
}
