package com.linpj.novel.create.rule;

import com.linpj.novel.create.pojo.entity.NovelPlotThread;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.service.PlotThreadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RC-05 知识边界规则测试（v2.0 M2-C5：闭世界判定——查询 whoKnows，不穷举不知道）
 *
 * @author HL
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeBoundaryRuleTest {

    @Mock private PlotThreadService plotThreadService;
    @Mock private CharacterCardsService characterCardsService;

    private KnowledgeBoundaryRule rule;

    @BeforeEach
    void setUp() {
        rule = new KnowledgeBoundaryRule(plotThreadService, characterCardsService);
        lenient().when(characterCardsService.getCharacterCardsByProjectId(1L))
                .thenReturn(List.of(card(1L, "林砚"), card(2L, "顾昭"), card(3L, "老周")));
        lenient().when(plotThreadService.listSecretThreads(1L))
                .thenReturn(List.of(secret(11L, "师父死因", List.of(1L))));
    }

    private CharacterCardsVo card(Long id, String name) {
        CharacterCardsVo vo = new CharacterCardsVo();
        vo.setId(id);
        vo.setName(name);
        return vo;
    }

    private NovelPlotThread secret(Long id, String key, List<Long> whoKnows) {
        NovelPlotThread thread = new NovelPlotThread();
        thread.setId(id);
        thread.setBookId(1L);
        thread.setDescription("秘密" + id);
        thread.setStatus("OPEN");
        thread.setSecretKey(key);
        thread.setWhoKnows(whoKnows);
        return thread;
    }

    private RuleCheckContext draftContext(String draft) {
        RuleCheckContext ctx = RuleCheckContext.of(RuleStage.DRAFT);
        ctx.setBookId(1L);
        ctx.setDraft(draft);
        return ctx;
    }

    @Test
    void 知情者提及秘密合法() {
        List<RuleViolation> violations = rule.check(draftContext("""
                林砚沉声道：“我终于查清了师父死因，另有隐情。”
                顾昭默然。
                """));

        assertTrue(violations.isEmpty(), "林砚在知情集合内：对白提及合法");
    }

    @Test
    void 知情集合外角色对白泄密违规() {
        List<RuleViolation> violations = rule.check(draftContext("""
                顾昭低声道：“你也别再瞒了，师父死因跟我说的不一样。”
                """));

        assertEquals(1, violations.size());
        assertEquals(RuleSeverity.HIGH, violations.get(0).getSeverity());
        assertTrue(violations.get(0).getEvidence().contains("顾昭"));
        assertTrue(violations.get(0).getEvidence().contains("伏笔#11"));
        assertTrue(violations.get(0).getEvidence().contains("师父死因"));
    }

    @Test
    void 陷阱用例_叙述层提及不算角色泄密() {
        List<RuleViolation> violations = rule.check(draftContext("""
                真相是：师父死因另有隐情，只是无人知晓。顾昭转身离去。
                """));

        assertTrue(violations.isEmpty(), "叙述层是作者声音（提前剧透属另一类问题），不做知识边界判定");
    }

    @Test
    void 陷阱用例_归属失败跳过不误判() {
        List<RuleViolation> violations = rule.check(draftContext("""
                黑暗中响起一个声音：“嘿嘿，师父死因嘛，我早知道了。”
                """));

        assertTrue(violations.isEmpty(), "引号前无角色名：归属失败宁可漏检");
    }

    @Test
    void 无secretKey或无秘密线程静默() {
        // secretKey 缺失：只维护集合不判文本
        when(plotThreadService.listSecretThreads(1L))
                .thenReturn(List.of(secret(12L, null, List.of(1L))));
        assertTrue(rule.check(draftContext("顾昭说：“师父死因有诈！”")).isEmpty(),
                "无文本锚点的秘密不判（数据缺失≠违规）");

        // 无秘密线程
        when(plotThreadService.listSecretThreads(1L)).thenReturn(List.of());
        assertTrue(rule.check(draftContext("随便写什么")).isEmpty());

        // 数据通道故障
        when(plotThreadService.listSecretThreads(1L)).thenThrow(new RuntimeException("库不可用"));
        assertTrue(rule.check(draftContext("正文")).isEmpty(), "数据故障静默不炸管道");
    }

    @Test
    void 陷阱用例_窗口外的角色名不充当说话人() {
        // 老周距引号超过 30 字符：窗口内无角色名，归属失败跳过
        List<RuleViolation> violations = rule.check(draftContext("""
                老周在天边放鹰，鹰唳长空，声震四野，久久不散。许久之后，虚空里传来低语：“师父死因，我已知之。”
                """));

        assertTrue(violations.isEmpty(), "说话人归属仅认引号紧邻窗口内的最近角色名，窗口外名字不得误绑");
    }
}
