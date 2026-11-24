package com.linpj.novel.create.worldstate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelWikiConflictMapper;
import com.linpj.novel.create.pojo.entity.NovelWikiConflict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 正典冲突仲裁服务实现
 *
 * @author HL
 */
@Service
public class WikiConflictServiceImpl extends ServiceImpl<NovelWikiConflictMapper, NovelWikiConflict>
        implements WikiConflictService {

    private static final Logger log = LoggerFactory.getLogger(WikiConflictServiceImpl.class);

    private static final String STATUS_PENDING = "PENDING_REVIEW";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_REJECTED = "REJECTED";

    @Override
    public void record(Long bookId, Long chapterId, Long characterId,
                       String conflictKind, String oldValue, String newValue, String status, String note) {
        if (bookId == null || conflictKind == null || status == null) {
            return;
        }
        try {
            NovelWikiConflict conflict = new NovelWikiConflict();
            conflict.setBookId(bookId);
            conflict.setChapterId(chapterId);
            conflict.setCharacterId(characterId);
            conflict.setConflictKind(conflictKind);
            conflict.setOldValue(oldValue);
            conflict.setNewValue(newValue);
            conflict.setStatus(status);
            conflict.setNote(note);
            conflict.setCreatedAt(new Date());
            this.save(conflict);
        } catch (Exception e) {
            // 冲突记账是审计能力：失败只告警，不得反阻塞定稿结算主链路
            log.error("冲突账本写入失败（不阻塞结算）: bookId={}, kind={}, {}->{}",
                    bookId, conflictKind, oldValue, newValue, e);
        }
    }

    @Override
    public List<NovelWikiConflict> listPendingReview(Long bookId) {
        return this.list(new LambdaQueryWrapper<NovelWikiConflict>()
                .eq(bookId != null, NovelWikiConflict::getBookId, bookId)
                .eq(NovelWikiConflict::getStatus, STATUS_PENDING)
                .orderByAsc(NovelWikiConflict::getId));
    }

    @Override
    public void resolve(Long conflictId, boolean accept, String note) {
        if (conflictId == null) {
            return;
        }
        NovelWikiConflict update = new NovelWikiConflict();
        update.setId(conflictId);
        update.setStatus(accept ? STATUS_RESOLVED : STATUS_REJECTED);
        update.setNote(note);
        update.setResolvedAt(new Date());
        this.updateById(update);
    }
}
