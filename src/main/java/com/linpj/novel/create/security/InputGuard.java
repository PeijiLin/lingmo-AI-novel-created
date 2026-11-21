package com.linpj.novel.create.security;

import com.linpj.novel.create.pojo.dto.AiChatRequest;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输入前置防线（v2.0 设计 7.1 前置层 / 7.3 输入与语义边界 / M4-E1）
 *
 * 两类判定（纯代码，无 LLM——前置门槛必须确定性、零成本、零延迟）：
 * 1. 约束可满足性预检：参数在物理上不可满足/明显滥用（负字数、单章 10 万字、超长输入灌爆上下文）
 *    ——"3000 字写 50 场景"类矛盾在源头直接拒绝并给替代，绝不静默生成
 * 2. 提示注入模式识别：
 *    - 高置信模式（P1 拒绝）：直接命中"忽略以上指令/泄露系统提示词/开发者模式"等操纵话术
 *    - 可疑模式（P2 标记放行）："你现在是系统/请扮演管理员"等弱信号——小说题材（AI 角色文）
 *      合法包含此类表述，宁可记不可杀（拦截率与误杀率必须同时报告，v2.0 §7.4）
 *
 * 精度纪律：P1 模式表只收高置信话术；一切题材敏感的弱模式一律进 P2。
 *
 * @author HL
 */
@Component
public class InputGuard {

    /** 用户输入长度上限（约 1.2 万 token 意图灌水线；正常创作意图远低于此） */
    static final int MAX_USER_INPUT_CHARS = 20_000;

    /** 标题长度上限 */
    static final int MAX_TITLE_CHARS = 200;

    /** 单章目标字数上限（单章超此值物理上不合理，属灌水/误填） */
    static final int MAX_TARGET_WORD_COUNT = 30_000;

    /** 高置信注入模式（P1：拒绝） */
    private static final Pattern[] INJECTION_PATTERNS = {
            // 指令覆盖话术
            Pattern.compile("忽略(以上|上述|之前|前面)(所有|全部)?(的)?(规则|指令|提示|设定|约束)"),
            Pattern.compile("无视(以上|上述|之前|前面)(所有|全部)?(的)?(规则|指令|约束)"),
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions?|rules?|prompts?)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(the\\s+)?(above|previous|prior)\\s+(instructions?|rules?)"),
            // 系统提示词窃取（动词与密件名词之间允许少量连接词，容纳"你的系统提示词"等组合）
            Pattern.compile("(输出|泄露|展示|打印|复述)[^。,，!！?？\\n]{0,6}(提示词?|系统提示词|指令集|系统指令|prompt)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(reveal|show|print|repeat)\\s+your\\s+(system\\s+)?prompt"),
            // 越狱模式
            Pattern.compile("(进入|开启|切换到)(开发者?模式|developer mode|DAN ?模式|越狱模式)"),
            Pattern.compile("(?i)\\bDAN\\b.{0,12}(mode|模式)|(do anything now)"),
    };

    /** 可疑弱模式（P2：标记放行——AI 角色文/系统流题材合法包含，只记不杀） */
    private static final Pattern[] SUSPICIOUS_PATTERNS = {
            Pattern.compile("(你现在是|从现在起你是|从现在开始你是)"),
            Pattern.compile("请扮演一个?(系统|管理员|开发者|AI ?助手|人工智能)"),
            Pattern.compile("(?i)you are now (an?|the) (system|admin|developer|assistant)"),
            Pattern.compile("(?i)jailbreak"),
    };

    /**
     * 前置预检：约束可满足性 + 注入模式。合法返回 pass()。
     */
    public PrecheckVerdict precheck(AiChatRequest request) {
        if (request == null) {
            return PrecheckVerdict.reject("INPUT_ABUSE", "请求为空", "检查请求体后重试");
        }
        // —— 1. 约束可满足性（先物理后语义：不可满足的参数无需语义分析）——
        if (request.getTargetWordCount() != null) {
            if (request.getTargetWordCount() <= 0) {
                return PrecheckVerdict.reject("CONSTRAINT_VIOLATION",
                        "目标字数必须为正数（收到 " + request.getTargetWordCount() + "）",
                        "填写 1500-5000 的正整数；不填则使用模式默认字数");
            }
            if (request.getTargetWordCount() > MAX_TARGET_WORD_COUNT) {
                return PrecheckVerdict.reject("CONSTRAINT_VIOLATION",
                        "单章目标字数超出合理范围（上限 " + MAX_TARGET_WORD_COUNT + "，收到 "
                                + request.getTargetWordCount() + "）",
                        "长内容请按章节分段生成，单章建议 1500-5000 字");
            }
        }
        if (request.getTitle() != null && request.getTitle().length() > MAX_TITLE_CHARS) {
            return PrecheckVerdict.reject("CONSTRAINT_VIOLATION",
                    "章节标题过长（上限 " + MAX_TITLE_CHARS + " 字，收到 " + request.getTitle().length() + "）",
                    "提炼简短标题，长内容留在正文或创作意图里");
        }
        if (request.getUserInput() != null && request.getUserInput().length() > MAX_USER_INPUT_CHARS) {
            return PrecheckVerdict.reject("CONSTRAINT_VIOLATION",
                    "创作意图超长（上限 " + MAX_USER_INPUT_CHARS + " 字，收到 " + request.getUserInput().length()
                            + " 字），上下文窗口无法承载",
                    "提炼本章核心意图（人物/冲突/走向），背景信息存入书籍设定而非创作意图");
        }

        // —— 2. 注入模式识别（userInput 是主攻击面；title 顺带）——
        String userInput = nullToEmpty(request.getUserInput());
        String title = nullToEmpty(request.getTitle());
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userInput).find() || pattern.matcher(title).find()) {
                return PrecheckVerdict.reject("PROMPT_INJECTION",
                        "创作意图中检测到针对生成系统的指令操纵话术，已拒绝（内容不会被执行）",
                        "请直接描述你的创作意图（情节/人物/氛围），而不是给系统的指令；"
                                + "如需改变生成行为，请使用模式/参数等正式配置项");
            }
        }
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(userInput).find() || pattern.matcher(title).find()) {
                // P2：题材敏感（AI 角色文合法包含），放行记审计
                return PrecheckVerdict.flag("SUSPECTED_INJECTION",
                        "疑似系统角色扮演表述（AI 题材合法常见，放行并记录观测）");
            }
        }
        return PrecheckVerdict.pass();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
