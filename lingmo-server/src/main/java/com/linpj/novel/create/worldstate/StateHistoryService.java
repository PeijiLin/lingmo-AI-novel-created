package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelStateFactMapper;
import com.linpj.novel.create.pojo.entity.NovelStateFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 人物状态事实账本服务（v2.0 设计 6.3 / M2-C3：三时间轴状态求解）
 *
 * - appendFact：新事实入账 + 旧事实软失效（validTo = 新区间前章，永不物理删除——v2.0 §1.3 事件溯源）
 * - solveState(atChapter)：取区间覆盖该章的最新事实——倒叙场景的"回忆目标时刻"求解入口（R-03 修复）
 * - solveLatest：最新有效事实（当前态投影的权威来源）
 *
 * 单一写入者：仅定稿结算链（ChapterFinalizeService）追加；triTimeline 部署开关关闭时
 * 不写本表（现状行为：仅覆盖当前态投影）。
 *
 * @author HL
 */
@Service
public class StateHistoryService extends ServiceImpl<NovelStateFactMapper, NovelStateFact> {

    private static final Logger log = LoggerFactory.getLogger(StateHistoryService.class);

    /**
     * 事实入账：追加新事实行，并将既有未失效事实软失效到新区间之前。
     *
     * @param fromChapter 新事实生效起始章（通常为快照来源章）
     * @return 入账事实行（失败返回 null，不抛——账本维护不反阻塞定稿）
     */
    public NovelStateFact appendFact(Long bookId, Long characterId, Long fromChapter, Object state) {
        if (bookId == null || characterId == null || fromChapter == null || state == null) {
            return null;
        }
        try {
            // 1. 既有未失效事实软失效（事实不可变思想：只更新失效边界，不改内容）
            List<NovelStateFact> openFacts = this.list(new LambdaQueryWrapper<NovelStateFact>()
                    .eq(NovelStateFact::getBookId, bookId)
                    .eq(NovelStateFact::getCharacterId, characterId)
                    .isNull(NovelStateFact::getValidToChapter)
                    .lt(NovelStateFact::getValidFromChapter, fromChapter));
            for (NovelStateFact open : openFacts) {
                NovelStateFact close = new NovelStateFact();
                close.setId(open.getId());
                close.setValidToChapter(fromChapter);
                this.updateById(close);
            }

            // 2. 追加新事实
            NovelStateFact fact = new NovelStateFact();
            fact.setBookId(bookId);
            fact.setCharacterId(characterId);
            fact.setSourceChapter(fromChapter);
            fact.setValidFromChapter(fromChapter);
            fact.setValidToChapter(null); // 当前有效
            fact.setState(state);
            this.save(fact);
            return fact;
        } catch (Exception e) {
            // 同一章节重复入账（validFrom == fromChapter 的旧事实）：视为幂等覆盖，
            // 上一 save 已追加同区间行，solveState 取最新——账本冗余但语义正确，不阻塞
            log.error("状态事实入账失败（不阻塞结算）: bookId={}, characterId={}, fromChapter={}",
                    bookId, characterId, fromChapter, e);
            return null;
        }
    }

    /**
     * 按叙述时刻求解状态（倒叙/回溯核心入口，TC-08）。
     *
     * 区间过滤在内存侧显式判定（每书每人事实行数 = 章数级别，无性能问题）——
     * 避免 SQL 魔法与语义漂移，也让倒叙正确性可单测。
     *
     * @param atChapter 叙述时刻（倒叙场景传"回忆目标章"，正常场景传当前章）
     * @return 覆盖该章的最新事实；无记录返回 null
     */
    public NovelStateFact solveState(Long bookId, Long characterId, Long atChapter) {
        if (bookId == null || characterId == null || atChapter == null) {
            return null;
        }
        return this.list(new LambdaQueryWrapper<NovelStateFact>()
                .eq(NovelStateFact::getBookId, bookId)
                .eq(NovelStateFact::getCharacterId, characterId)
                .orderByDesc(NovelStateFact::getValidFromChapter))
                .stream()
                .filter(fact -> covers(fact, atChapter))
                .findFirst() // 已按 validFrom 降序：首个覆盖即最新
                .orElse(null);
    }

    /**
     * 区间覆盖判定（纯函数，倒叙正确性的核心语义）：
     * validFrom <= atChapter < validTo（validTo 为 null 表示开放区间）。
     */
    public static boolean covers(NovelStateFact fact, Long atChapter) {
        if (fact == null || fact.getValidFromChapter() == null || atChapter == null) {
            return false;
        }
        if (fact.getValidFromChapter() > atChapter) {
            return false;
        }
        return fact.getValidToChapter() == null || fact.getValidToChapter() > atChapter;
    }

    /** 最新有效事实（当前态的权威来源；无返回 null） */
    public NovelStateFact solveLatest(Long bookId, Long characterId) {
        if (bookId == null || characterId == null) {
            return null;
        }
        return this.list(new LambdaQueryWrapper<NovelStateFact>()
                .eq(NovelStateFact::getBookId, bookId)
                .eq(NovelStateFact::getCharacterId, characterId)
                .orderByDesc(NovelStateFact::getId))
                .stream()
                .filter(fact -> fact.getValidToChapter() == null)
                .findFirst()
                .orElse(null);
    }
}
