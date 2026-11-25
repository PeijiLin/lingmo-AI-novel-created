package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.entity.NovelCanonProposal;
import com.linpj.novel.create.pojo.entity.NovelStateFact;
import com.linpj.novel.create.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 正典提案提交门（自主性改造 R3：§2——proposal.emit 暂存的闭环消费）
 *
 * <p>Writer L2 节点产出被采纳（章终稿落定）后，统一裁定该 run 暂存的 PENDING 提案：
 * <ul>
 *   <li><b>ADOPTED</b>：软字段演化（伤势/位置等状态变更）——appendFact 写入正典
 *       fact 账本（对齐 ChapterFinalizeService 抽取入账的"软信息直接入账"惯例）；</li>
 *   <li><b>REJECTED</b>：生死硬冲突（现有 DEAD vs 提案 ALIVE，或反向）——保留旧状态、
 *       落 wiki_conflict 待作者仲裁（对齐 applyStateWithArbitration 的 R3 死亡回退
 *       哲学：宁可阻塞入账也不把"复活"静默写进正典）；结构不完整同样拒。</li>
 * </ul>
 *
 * <p>裁定幂等：PENDING→终态单向（markResolved 不覆盖已裁定）；消费静默容错——
 * 提交门是终稿后的旁路闭环，失败保持 PENDING 待下次终稿重试，绝不阻断出章。
 *
 * @author HL
 */
@Service
public class ProposalCommitGate {

    private static final Logger log = LoggerFactory.getLogger(ProposalCommitGate.class);

    private final CanonProposalService proposalService;
    private final StateHistoryService stateHistoryService;
    private final WikiConflictService wikiConflictService;

    public ProposalCommitGate(CanonProposalService proposalService,
                              StateHistoryService stateHistoryService,
                              WikiConflictService wikiConflictService) {
        this.proposalService = proposalService;
        this.stateHistoryService = stateHistoryService;
        this.wikiConflictService = wikiConflictService;
    }

    /**
     * 终稿采纳后的提案统一裁定（Writer L2 节点 SUCCESS 的提交门语义）。
     *
     * @return 裁定摘要（adopted/rejected 计数；供日志与观测）
     */
    public SettleSummary settle(Long runId, String nodeId, Long bookId, Long chapterId) {
        int adopted = 0;
        int rejected = 0;
        if (runId == null || nodeId == null) {
            return new SettleSummary(0, 0);
        }
        List<NovelCanonProposal> proposals;
        try {
            proposals = proposalService.proposalsOf(runId, nodeId);
        } catch (Exception e) {
            return new SettleSummary(0, 0);
        }
        for (NovelCanonProposal proposal : proposals) {
            if (proposal == null || !CanonProposalService.STATUS_PENDING.equals(proposal.getStatus())) {
                continue;
            }
            Verdict verdict = judge(proposal, bookId, chapterId);
            if (CanonProposalService.STATUS_PENDING.equals(verdict.status())) {
                continue; // 入账失败：保持 PENDING 待下次终稿重试（不虚报、不改判）
            }
            if (proposalService.markResolved(proposal.getId(), verdict.status())) {
                if (CanonProposalService.STATUS_ADOPTED.equals(verdict.status())) {
                    adopted++;
                } else {
                    rejected++;
                }
            }
        }
        if (adopted + rejected > 0) {
            log.info("提案提交门裁定: run={}, node={}, adopted={}, rejected={}",
                    runId, nodeId, adopted, rejected);
        }
        return new SettleSummary(adopted, rejected);
    }

    /** 单提案裁定：结构 → 生死冲突 → 软字段入账 */
    private Verdict judge(NovelCanonProposal proposal, Long bookId, Long chapterId) {
        Map<String, Object> proposalMap = parseProposal(proposal.getProposal());
        if (proposalMap == null || !(proposalMap.get("characterId") instanceof Number n)
                || !(proposalMap.get("state") instanceof Map<?, ?> stateMap) || stateMap.isEmpty()) {
            reject(proposal, bookId, chapterId, null, null,
                    "提案结构不完整（缺 characterId/state）");
            return new Verdict(CanonProposalService.STATUS_REJECTED);
        }
        long characterId = n.longValue();
        if (bookId == null) {
            reject(proposal, bookId, chapterId, characterId, null, "书上下文缺失");
            return new Verdict(CanonProposalService.STATUS_REJECTED);
        }

        // 生死硬冲突仲裁（对齐 applyStateWithArbitration 哲学）
        String proposedVital = vitalValue(stateMap);
        NovelStateFact existing = stateHistoryService.solveLatest(bookId, characterId);
        if (proposedVital != null && existing != null && existing.getState() instanceof Map<?, ?> existingState) {
            String existingVital = vitalValue(existingState);
            if (existingVital != null && !existingVital.equalsIgnoreCase(proposedVital)
                    && isVitalTransitionConflicting(existingVital, proposedVital)) {
                reject(proposal, bookId, chapterId, characterId, existingVital,
                        "生死硬冲突（" + existingVital + "→" + proposedVital + "），待作者仲裁");
                return new Verdict(CanonProposalService.STATUS_REJECTED);
            }
        }

        // 软字段演化：appendFact 入正典（fromChapter 传 chapterId——项目惯例见 ChapterFinalizeService）
        try {
            stateHistoryService.appendFact(bookId, characterId, chapterId, stateMap);
        } catch (Exception e) {
            // 入账失败保持 PENDING（下次终稿重试）——不虚报 ADOPTED
            log.warn("提案入账失败（保持 PENDING 待重试）: proposalId={}, error={}",
                    proposal.getId(), e.getMessage());
            return new Verdict(CanonProposalService.STATUS_PENDING);
        }
        return new Verdict(CanonProposalService.STATUS_ADOPTED);
    }

    /** 拒绝 + 冲突账本留痕（作者仲裁入口） */
    private void reject(NovelCanonProposal proposal, Long bookId, Long chapterId,
                        Long characterId, String oldValue, String reason) {
        try {
            wikiConflictService.record(bookId, chapterId, characterId,
                    "PROPOSAL_CONFLICT", oldValue, null, "PENDING_REVIEW",
                    "Writer L2 提案被提交门拒绝：" + reason);
        } catch (Exception e) {
            log.warn("冲突账本落账失败（提案仍拒、留痕缺失）: proposalId={}, error={}",
                    proposal.getId(), e.getMessage());
        }
    }

    /** 生死键识别（aliveStatus/生死/存活 等约定键；无生死键 = 软字段提案） */
    private static String vitalValue(Map<?, ?> state) {
        for (var entry : state.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.equalsIgnoreCase("aliveStatus") || key.contains("生死") || key.contains("存活")) {
                return entry.getValue() == null ? null : String.valueOf(entry.getValue());
            }
        }
        return null;
    }

    /** 生死迁移冲突判定：DEAD→ALIVE 死亡回退必仲裁；ALIVE→DEAD 自然演化（对齐既有仲裁语义） */
    private static boolean isVitalTransitionConflicting(String from, String to) {
        return "DEAD".equalsIgnoreCase(from) && "ALIVE".equalsIgnoreCase(to);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseProposal(Object proposal) {
        if (proposal == null) {
            return null;
        }
        try {
            String json = proposal instanceof String s ? s : JsonUtil.toJson(proposal);
            return JsonUtil.fromJson(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 裁定结果（PENDING = 入账失败保持暂存待重试） */
    private record Verdict(String status) {
    }

    /** 裁定摘要（观测/日志面） */
    public record SettleSummary(int adopted, int rejected) {
    }
}
