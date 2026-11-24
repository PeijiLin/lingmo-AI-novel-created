package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelPendingAssertionMapper;
import com.linpj.novel.create.pojo.entity.NovelPendingAssertion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 待证实断言服务（v2.0 设计 2.3 / M2-C6：非 FACT 断言隔离区）
 *
 * 生命周期：PENDING --（>=2 独立锚点证实 / 作者确认）--> CONFIRMED
 *                  --（30 章未证实）--> EXPIRED（归档不删除）
 *                   --（作者驳回）--> REJECTED
 *
 * 单一写入者：仅定稿结算链（ChapterFinalizeService）写入与推进；
 * CONFIRMED 后采纳进权威层走正规写入通道（再抽取为 FACT 时自然入账 / 作者编辑），
 * 本服务不代写状态表（避免双写者）。
 *
 * @author HL
 */
@Service
public class PendingAssertionService extends ServiceImpl<NovelPendingAssertionMapper, NovelPendingAssertion> {

    private static final Logger log = LoggerFactory.getLogger(PendingAssertionService.class);

    /** 升格所需的独立锚点证实数（v2.0 设计 2.3 定值） */
    public static final int PROMOTE_CONFIRMATIONS = 2;

    /** 待证实窗口：30 章 */
    public static final long EXPIRY_CHAPTERS = 30;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_REJECTED = "REJECTED";

    /** 记录待证实断言（payload 为抽取快照；expiresChapter = 来源章 + 30） */
    public void record(Long bookId, Long chapterId, Long characterId, String assertionType, Object payload) {
        if (bookId == null || assertionType == null || payload == null) {
            return;
        }
        try {
            NovelPendingAssertion assertion = new NovelPendingAssertion();
            assertion.setBookId(bookId);
            assertion.setChapterId(chapterId);
            assertion.setCharacterId(characterId);
            assertion.setAssertionType(assertionType);
            assertion.setPayload(payload);
            assertion.setStatus(STATUS_PENDING);
            assertion.setConfirmations(0);
            assertion.setExpiresChapter(chapterId == null ? null : chapterId + EXPIRY_CHAPTERS);
            assertion.setCreatedAt(new Date());
            this.save(assertion);
        } catch (Exception e) {
            // 隔离区是审计能力：写入失败不反阻塞定稿（该断言仅丢失隔离记录，不影响权威层无污染的既定事实）
            log.error("待证实断言写入失败（不阻塞结算）: bookId={}, type={}", bookId, assertionType, e);
        }
    }

    /** 待证实清单（人审队列数据源） */
    public List<NovelPendingAssertion> listPending(Long bookId) {
        return this.list(new LambdaQueryWrapper<NovelPendingAssertion>()
                .eq(NovelPendingAssertion::getBookId, bookId)
                .eq(NovelPendingAssertion::getStatus, STATUS_PENDING)
                .orderByAsc(NovelPendingAssertion::getId));
    }

    /**
     * 登记一次独立锚点证实；达到 2 次升格为 CONFIRMED（升格信号——采纳进权威层
     * 走后续正规写入通道，本服务不代写状态表）。
     */
    public void addConfirmation(Long assertionId) {
        if (assertionId == null) {
            return;
        }
        NovelPendingAssertion existing = this.getById(assertionId);
        if (existing == null || !STATUS_PENDING.equals(existing.getStatus())) {
            return;
        }
        int confirmations = (existing.getConfirmations() == null ? 0 : existing.getConfirmations()) + 1;
        NovelPendingAssertion update = new NovelPendingAssertion();
        update.setId(assertionId);
        update.setConfirmations(confirmations);
        if (confirmations >= PROMOTE_CONFIRMATIONS) {
            update.setStatus(STATUS_CONFIRMED);
            update.setResolvedAt(new Date());
            log.info("断言升格为已证实（>=2 独立锚点）: assertionId={}", assertionId);
        }
        this.updateById(update);
    }

    /** 作者仲裁：accept=确认（CONFIRMED）/ 驳回（REJECTED） */
    public void resolve(Long assertionId, boolean accept) {
        if (assertionId == null) {
            return;
        }
        NovelPendingAssertion update = new NovelPendingAssertion();
        update.setId(assertionId);
        update.setStatus(accept ? STATUS_CONFIRMED : STATUS_REJECTED);
        update.setResolvedAt(new Date());
        this.updateById(update);
    }

    /**
     * 过期清扫（定稿时按本书推进）：PENDING 且 expiresChapter < currentChapter -> EXPIRED。
     * 语义过滤在内存侧显式判定（与 StateHistoryService.covers 同风格——判定可单测）。
     *
     * @return 本次归档条数（审计用）
     */
    public int expireOutdated(Long bookId, Long currentChapterId) {
        if (bookId == null || currentChapterId == null) {
            return 0;
        }
        try {
            List<NovelPendingAssertion> candidates = this.list(new LambdaQueryWrapper<NovelPendingAssertion>()
                    .eq(NovelPendingAssertion::getBookId, bookId)
                    .isNotNull(NovelPendingAssertion::getExpiresChapter));
            int expired = 0;
            for (NovelPendingAssertion assertion : candidates) {
                if (!STATUS_PENDING.equals(assertion.getStatus()) || assertion.getExpiresChapter() == null) {
                    continue;
                }
                if (assertion.getExpiresChapter() >= currentChapterId) {
                    continue;
                }
                NovelPendingAssertion update = new NovelPendingAssertion();
                update.setId(assertion.getId());
                update.setStatus(STATUS_EXPIRED);
                update.setResolvedAt(new Date());
                this.updateById(update);
                expired++;
            }
            if (expired > 0) {
                log.info("待证实断言过期归档: bookId={}, count={}", bookId, expired);
            }
            return expired;
        } catch (Exception e) {
            log.warn("断言过期清扫失败（不影响结算）: bookId={}, {}", bookId, e.getMessage());
            return 0;
        }
    }
}
