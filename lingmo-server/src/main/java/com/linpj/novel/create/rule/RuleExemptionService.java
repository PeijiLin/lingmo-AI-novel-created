package com.linpj.novel.create.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelRuleExemptionMapper;
import com.linpj.novel.create.pojo.entity.NovelRuleExemption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则误报豁免 / 保护区声明服务（v2.0 M3-D6 误报闭环 + M4-E5 叙诡保护区）
 *
 * 闭环链路：作者标记误报或声明保护区(declare) -> 规则对本书静默或只告警
 * （装配进 RuleCheckContext）-> 豁免/保护区清单即回归集必须重跑（实验纪律，见实施计划 M3-D6）。
 *
 * 两档语义（E5 拆分，避免"保叙诡章就得整本关规则"）：
 * - 整本豁免（无章节区间）：规则完全跳过，D6 原语义；
 * - 区间保护（有章节区间）：区间内只告警不阻塞，区间外照常拦截；
 * 两者都支持 reviewAt 到期复核，过期即失效并进入复核队列。
 *
 * @author HL
 */
@Service
public class RuleExemptionService extends ServiceImpl<NovelRuleExemptionMapper, NovelRuleExemption> {

    private static final Logger log = LoggerFactory.getLogger(RuleExemptionService.class);

    /** 作者标记误报（整本豁免，幂等：同书同规则已豁免则跳过） */
    public boolean exempt(Long bookId, String ruleId, String reason) {
        if (bookId == null || ruleId == null || ruleId.isBlank()) {
            return false;
        }
        try {
            if (isExempted(bookId, ruleId)) {
                return true; // 幂等
            }
            NovelRuleExemption exemption = new NovelRuleExemption();
            exemption.setBookId(bookId);
            exemption.setRuleId(ruleId.trim());
            exemption.setReason(reason);
            exemption.setCreatedAt(new Date());
            this.save(exemption);
            log.info("规则误报豁免[回归集+1]: bookId={}, ruleId={}, reason={}", bookId, ruleId, reason);
            return true;
        } catch (Exception e) {
            // 豁免写入失败不炸接口；下次标记幂等重试
            log.error("豁免写入失败: bookId={}, ruleId={}, {}", bookId, ruleId, e.getMessage());
            return false;
        }
    }

    /**
     * 声明保护区（E5）：章节区间 [chapterFrom, chapterTo] 内该规则只告警不阻塞；
     * reviewAt 为空表示长期有效，到期后自动失效并进入复核队列。
     * 同书同规则幂等覆盖（UNIQUE(book_id, rule_id)），避免为同一规则堆叠多条互相矛盾的区间。
     */
    public boolean declareZone(Long bookId, String ruleId, Long chapterFrom, Long chapterTo,
                              Date reviewAt, String reason) {
        if (bookId == null || ruleId == null || ruleId.isBlank()) {
            return false;
        }
        if (chapterFrom != null && chapterTo != null && chapterFrom > chapterTo) {
            log.warn("保护区区间非法（from > to）: bookId={}, ruleId={}, {}..{}", bookId, ruleId, chapterFrom, chapterTo);
            return false;
        }
        try {
            NovelRuleExemption existing = findOne(bookId, ruleId);
            if (existing == null) {
                NovelRuleExemption exemption = new NovelRuleExemption();
                exemption.setBookId(bookId);
                exemption.setRuleId(ruleId.trim());
                exemption.setReason(reason);
                exemption.setChapterFrom(chapterFrom);
                exemption.setChapterTo(chapterTo);
                exemption.setReviewAt(reviewAt);
                exemption.setCreatedAt(new Date());
                this.save(exemption);
            } else {
                existing.setChapterFrom(chapterFrom);
                existing.setChapterTo(chapterTo);
                existing.setReviewAt(reviewAt);
                if (reason != null && !reason.isBlank()) {
                    existing.setReason(reason);
                }
                this.updateById(existing);
            }
            log.info("保护区声明[回归集+1]: bookId={}, ruleId={}, 区间={}..{}, 复核={}",
                    bookId, ruleId, chapterFrom, chapterTo, reviewAt);
            return true;
        } catch (Exception e) {
            log.error("保护区声明失败: bookId={}, ruleId={}, {}", bookId, ruleId, e.getMessage());
            return false;
        }
    }

    /** 续期/调整复核时间（reviewAt 为空 = 取消到期，长期有效） */
    public boolean renew(Long bookId, String ruleId, Date reviewAt) {
        if (bookId == null || ruleId == null || ruleId.isBlank()) {
            return false;
        }
        NovelRuleExemption existing = findOne(bookId, ruleId);
        if (existing == null) {
            return false;
        }
        existing.setReviewAt(reviewAt);
        return this.updateById(existing);
    }

    /** 撤销豁免（作者收回误报标记或规则修正后恢复检查） */
    public boolean revoke(Long bookId, String ruleId) {
        if (bookId == null || ruleId == null) {
            return false;
        }
        return this.remove(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId)
                .eq(NovelRuleExemption::getRuleId, ruleId));
    }

    public boolean isExempted(Long bookId, String ruleId) {
        return this.count(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId)
                .eq(NovelRuleExemption::getRuleId, ruleId)) > 0;
    }

    /**
     * 本书整本豁免的规则清单（编排器装配 RuleCheckContext 用）。
     * 只包含"无章节区间且未过期"的行：区间声明走 {@link #listProtectedRules}，过期声明一律失效。
     */
    public Set<String> listExemptedRules(Long bookId) {
        Set<String> ruleIds = new HashSet<>();
        if (bookId == null) {
            return ruleIds;
        }
        Date now = new Date();
        for (NovelRuleExemption exemption : listExemptions(bookId)) {
            RuleExemptionScope scope = RuleExemptionScope.from(exemption);
            if (scope == null || scope.ruleId() == null || scope.ruleId().isBlank()) {
                continue;
            }
            if (scope.coversWholeBook() && !scope.isExpired(now)) {
                ruleIds.add(scope.ruleId());
            }
        }
        return ruleIds;
    }

    /**
     * 本章受区间保护的规则清单（E5）：区间覆盖本章且未过期的规则 -> 只告警不阻塞。
     * 章节号缺失或服务无数据时返回空集（保守：宁可拦截也不默认豁免）。
     */
    public Set<String> listProtectedRules(Long bookId, Long chapterId) {
        Set<String> ruleIds = new HashSet<>();
        if (bookId == null || chapterId == null) {
            return ruleIds;
        }
        Date now = new Date();
        for (NovelRuleExemption exemption : listExemptions(bookId)) {
            RuleExemptionScope scope = RuleExemptionScope.from(exemption);
            if (scope == null || scope.coversWholeBook()) {
                continue;
            }
            if (scope.protects(chapterId, now)) {
                ruleIds.add(scope.ruleId());
            }
        }
        return ruleIds;
    }

    /** 到期复核队列：reviewAt 已到期的保护区/豁免（人审提醒续期或撤回） */
    public List<NovelRuleExemption> listDueForReview(Long bookId) {
        if (bookId == null) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId)
                .isNotNull(NovelRuleExemption::getReviewAt)
                .le(NovelRuleExemption::getReviewAt, new Date())
                .orderByAsc(NovelRuleExemption::getReviewAt));
    }

    /** 本书豁免明细（作者查询接口 + 回归集导出） */
    public List<NovelRuleExemption> listExemptions(Long bookId) {
        return this.list(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId)
                .orderByAsc(NovelRuleExemption::getId));
    }

    private NovelRuleExemption findOne(Long bookId, String ruleId) {
        return this.getOne(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId)
                .eq(NovelRuleExemption::getRuleId, ruleId.trim()), false);
    }
}
