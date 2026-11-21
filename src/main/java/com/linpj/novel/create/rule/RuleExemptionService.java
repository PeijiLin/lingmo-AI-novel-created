package com.linpj.novel.create.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelRuleExemptionMapper;
import com.linpj.novel.create.pojo.entity.NovelRuleExemption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则误报豁免服务（v2.0 M3-D6 误报闭环入口）
 *
 * 闭环链路：作者标记误报(record) -> 规则对本书静默(listExemptedRules 装配进 RuleCheckContext)
 * -> 规则调整时豁免清单即回归集必须重跑（实验纪律，见实施计划 M3-D6）。
 *
 * @author HL
 */
@Service
public class RuleExemptionService extends ServiceImpl<NovelRuleExemptionMapper, NovelRuleExemption> {

    private static final Logger log = LoggerFactory.getLogger(RuleExemptionService.class);

    /** 作者标记误报（幂等：同书同规则已豁免则跳过） */
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
            exemption.setCreatedAt(new java.util.Date());
            this.save(exemption);
            log.info("规则误报豁免[回归集+1]: bookId={}, ruleId={}, reason={}", bookId, ruleId, reason);
            return true;
        } catch (Exception e) {
            // 豁免写入失败不炸接口；下次标记幂等重试
            log.error("豁免写入失败: bookId={}, ruleId={}, {}", bookId, ruleId, e.getMessage());
            return false;
        }
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

    /** 本书豁免规则清单（编排器装配 RuleCheckContext 用） */
    public Set<String> listExemptedRules(Long bookId) {
        Set<String> ruleIds = new HashSet<>();
        if (bookId == null) {
            return ruleIds;
        }
        List<NovelRuleExemption> exemptions = this.list(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId));
        exemptions.stream()
                .filter(e -> e.getRuleId() != null && !e.getRuleId().isBlank())
                .forEach(e -> ruleIds.add(e.getRuleId()));
        return ruleIds;
    }

    /** 本书豁免明细（作者查询接口 + 回归集导出） */
    public List<NovelRuleExemption> listExemptions(Long bookId) {
        return this.list(new LambdaQueryWrapper<NovelRuleExemption>()
                .eq(NovelRuleExemption::getBookId, bookId)
                .orderByAsc(NovelRuleExemption::getId));
    }
}
