package com.linpj.novel.create.worldstate;

import com.linpj.novel.create.pojo.entity.NovelWikiConflict;

import java.util.List;

/**
 * 正典冲突仲裁服务（v2.0 设计 6.4，M1-B4 落地 R1 自动生效 / R3 人审留痕）
 *
 * 单一写入者原则（红线 3）：冲突账本只由章节定稿结算链（ChapterFinalizeService）写入，
 * 前端/Agent 不得直接写本表；作者仲裁动作（RESOLVED/REJECTED）为唯一的人工更新通道。
 *
 * @author HL
 */
public interface WikiConflictService {

    /**
     * 记录冲突条目（幂等约束：调用方保证只在状态判定分叉点写入）。
     *
     * @param conflictKind STATE_EVOLVE（自然演化，自动生效留痕）/ RESURRECTION_REVIEW（死亡回退，待审）
     * @param status       AUTO_APPLIED / PENDING_REVIEW
     */
    void record(Long bookId, Long chapterId, Long characterId,
                String conflictKind, String oldValue, String newValue, String status, String note);

    /** 待人审冲突清单（作者仲裁队列数据源） */
    List<NovelWikiConflict> listPendingReview(Long bookId);

    /**
     * 作者仲裁：RESOLVED（确认新事实）/ REJECTED（维持旧事实）。
     * RESOLVED 仅更新冲突条目状态；权威层生效由作者通过正规写入通道（状态编辑）完成，
     * 本服务不代为改状态表（避免双写者）。
     */
    void resolve(Long conflictId, boolean accept, String note);
}
