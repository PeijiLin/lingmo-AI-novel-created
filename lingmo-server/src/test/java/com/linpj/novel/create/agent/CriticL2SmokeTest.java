package com.linpj.novel.create.agent;

import com.linpj.novel.create.config.AutonomyProperties;
import com.linpj.novel.create.pojo.pipeline.CreationMode;
import com.linpj.novel.create.pojo.pipeline.CriticContext;
import com.linpj.novel.create.pojo.pipeline.Issue;
import com.linpj.novel.create.pojo.pipeline.PipelineRequest;
import com.linpj.novel.create.pojo.pipeline.ReviewReport;
import com.linpj.novel.create.pojo.pipeline.Scene;
import com.linpj.novel.create.pojo.pipeline.ChapterOutline;
import com.linpj.novel.create.worldstate.AgentStepService;
import com.linpj.novel.create.worldstate.CitationLinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

/**
 * Critic L2 真实模型冒烟（自主性改造 R1b 验收：R1 原方案验收标准的数字面首测）
 *
 * <p><b>烧钱测试——默认跳过</b>：仅 {@code mvn -Dtest=CriticL2SmokeTest -Dsmoke=true test} 手动执行，
 * 不进常规回归（不烧 token 的 CI 纪律）。
 *
 * <p>冒烟设计（播种矛盾检出）：
 * <ul>
 *   <li>正典事实（novel_state_fact，代码外播种）：主角林岸（characterId=1001）<b>左臂</b>中弹恢复期；</li>
 *   <li>CriticContext 材料：只给"曾有战斗受伤经历"（不写哪只手臂——L1/L2 同源材料）；</li>
 *   <li>待审正文：写<b>右臂</b>隐隐作痛 + 抬枪瞄准（与正典矛盾）；</li>
 *   <li><b>L2 预期</b>：模型主动 canon.query 取证 → 发现左臂 → 报 CHARACTER_OOC（HIGH）+ evidenceId；</li>
 *   <li><b>L1 对照</b>：单次调用材料无实锤——检出能力对比即 E5 自主性消融的最小预演。</li>
 * </ul>
 *
 * <p>观察指标（控制台汇总）：L2 终态/步数/token/检出、citation_link 引文有效率、L1 检出、
 * 步账与回执账落库行数（审计链路完整性）。
 *
 * @author HL
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // knife4j/springdoc 文档自动配置在无 web 环境缺依赖——lazy 化绕开（业务 bean 由注入触发）
                "spring.main.lazy-initialization=true",
                "springdoc.api-docs.enabled=false"
        })
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "smoke", matches = "true")
class CriticL2SmokeTest {

    @Autowired private CriticL2Gateway criticL2Gateway;
    @Autowired private CriticAgent criticAgent;
    @Autowired private AutonomyProperties autonomy;
    @Autowired private AgentStepService stepService;
    @Autowired private CitationLinkService citationLinkService;

    private static final long BOOK_ID = 10L;
    private static final long RUN_ID = System.currentTimeMillis();
    private static final String NODE_ID = "SMOKE:CRITIC:1";

    private PipelineRequest request() {
        PipelineRequest request = new PipelineRequest();
        request.setBookId(BOOK_ID);
        request.setChapterId(999L);
        request.setMode(CreationMode.REFINED);
        request.setUserInput("续写林岸出院后的第一场对峙");
        return request;
    }

    private CriticContext context() {
        CriticContext context = new CriticContext();
        context.setDraft(seededContradictionDraft());
        context.setWorldSetting("现代都市，青州市。退役特种兵卷入旧案追查，写实风格。");
        // L1/L2 同源材料：只说"受伤经历"不写部位——取证能力差即消融变量
        context.setCharacterInfo("主角林岸（角色 ID 1001），退役侦察兵，第 12 章战斗中负伤出院，恢复期。"
                + "档案中可查其正典伤情状态。");
        context.setStoryDigest("第 11-12 章：林岸与旧部队战友重逢，遭伏击负伤入院。");
        context.setActivePlotThreads("伏笔 P-3：伏击者身份未明；伏笔 P-5：医院内有内应。");
        context.setOutline(outline());
        context.setUserInput(request().getUserInput());
        return context;
    }

    /** 播种矛盾正文：右臂痛 + 右臂抬枪（正典是左臂中弹） */
    private String seededContradictionDraft() {
        return """
                出院第三天，林岸站在废弃钢厂的阴影里。

                右臂又开始隐隐作痛——那颗该死的子弹留下的纪念。他甩了甩手腕，把酸麻压下去。

                对面传来脚步声。林岸深吸一口气，右臂抬起，枪口稳稳锁住集装箱的缺口。
                "出来吧，"他说，"我等你很久了。"

                阴影里的人影顿了顿，缓缓举起双手。
                """;
    }

    private ChapterOutline outline() {
        ChapterOutline outline = new ChapterOutline();
        outline.setChapterTitle("钢厂对峙");
        Scene scene = new Scene();
        scene.setLocation("废弃钢厂");
        scene.setConflict("林岸与伏击线索人的第一次正面接触");
        scene.setOutcome("对方现身，对峙形成");
        outline.setScenes(List.of(scene));
        return outline;
    }

    @Test
    void L2取证收口冒烟() {
        autonomy.setEnabled(true); // 冒烟显式开启总闸（测试内改内存实例，不动部署配置语义）

        long start = System.currentTimeMillis();
        CriticAgent.ReviewResult l2 =
                criticL2Gateway.review(request(), context(), RUN_ID, NODE_ID, null);
        long elapsed = System.currentTimeMillis() - start;

        ReviewReport report = l2.report();
        List<Issue> issues = report.getIssues() == null ? List.of() : report.getIssues();

        System.out.println("============== L2 冒烟结果 ==============");
        System.out.println("终态判断: " + (report.getSummary() != null && report.getSummary().contains("L2")
                ? "降级路径：" + report.getSummary() : "L2 收口产出"));
        System.out.println("approved=" + report.isApproved()
                + " overallScore=" + report.getOverallScore());
        System.out.println("检出问题数=" + issues.size());
        issues.forEach(i -> System.out.println("  - [" + i.getSeverity() + "] " + i.getType()
                + ": " + i.getProblem() + " | evidence=" + shorten(i.getEvidence())));
        System.out.println("L2 tokens=" + l2.tokens() + " 耗时=" + elapsed + "ms");
        System.out.println("步账行数=" + stepService.stepsOf(RUN_ID, NODE_ID).size()
                + "（真实持久化）");
        System.out.println("引文有效率=" + String.format("%.0f%%",
                citationLinkService.validRate(RUN_ID, NODE_ID) * 100)
                + "（citation_link 落账，E5 指标）");
        System.out.println("steps 明细: " + stepService.stepsOf(RUN_ID, NODE_ID).stream()
                .map(s -> s.getStepNo() + ":" + s.getKind() + "/" + s.getStatus()
                        + (s.getReceiptId() != null ? "(receipt=" + s.getReceiptId() + ")" : ""))
                .reduce((a, b) -> a + " → " + b).orElse("(空)"));
        System.out.println();
    }

    @Test
    void L1对照组冒烟() {
        long start = System.currentTimeMillis();
        CriticAgent.ReviewResult l1 = criticAgent.review(context());
        long elapsed = System.currentTimeMillis() - start;

        ReviewReport report = l1.report();
        List<Issue> issues = report.getIssues() == null ? List.of() : report.getIssues();

        System.out.println("============== L1 对照结果 ==============");
        System.out.println("approved=" + report.isApproved()
                + " overallScore=" + report.getOverallScore());
        System.out.println("检出问题数=" + issues.size() + "（材料无伤情实锤——取证能力差展示）");
        issues.forEach(i -> System.out.println("  - [" + i.getSeverity() + "] " + i.getType()
                + ": " + i.getProblem()));
        System.out.println("L1 tokens=" + l1.tokens() + " 耗时=" + elapsed + "ms");
        System.out.println();

        // 冒烟不设硬断言（模型行为有方差）——观察指标以控制台汇总为准
        org.junit.jupiter.api.Assertions.assertTrue(true);
    }

    private static String shorten(String text) {
        if (text == null) {
            return "(无)";
        }
        return text.length() <= 60 ? text : text.substring(0, 60) + "...";
    }
}
