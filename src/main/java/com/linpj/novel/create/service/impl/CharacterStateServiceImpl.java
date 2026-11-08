package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelCharacterStateMapper;
import com.linpj.novel.create.pojo.entity.NovelCharacterState;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.utils.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 人物动态状态服务实现
 *
 * @author HL
 */
@Service
public class CharacterStateServiceImpl extends ServiceImpl<NovelCharacterStateMapper, NovelCharacterState>
        implements CharacterStateService {

    @Override
    public String renderStates(Long bookId, List<Long> characterIds) {
        if (bookId == null) {
            return null;
        }
        List<NovelCharacterState> states = this.list(new LambdaQueryWrapper<NovelCharacterState>()
                .eq(NovelCharacterState::getBookId, bookId)
                .in(characterIds != null && !characterIds.isEmpty(),
                        NovelCharacterState::getCharacterId, characterIds));
        if (states.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (NovelCharacterState state : states) {
            sb.append("[角色#").append(state.getCharacterId()).append(" 当前状态] ")
                    .append(JsonUtil.toJson(state.getState())).append("\n");
        }
        return sb.toString();
    }

    @Override
    @Transactional
    public void updateState(Long bookId, Long characterId, Long chapterId, Object state) {
        if (bookId == null || characterId == null || state == null) {
            return;
        }
        NovelCharacterState existing = this.getOne(new LambdaQueryWrapper<NovelCharacterState>()
                .eq(NovelCharacterState::getBookId, bookId)
                .eq(NovelCharacterState::getCharacterId, characterId));
        if (existing == null) {
            NovelCharacterState record = new NovelCharacterState();
            record.setBookId(bookId);
            record.setCharacterId(characterId);
            record.setChapterId(chapterId);
            record.setState(state);
            record.setUpdatedAt(new Date());
            this.save(record);
        } else {
            NovelCharacterState update = new NovelCharacterState();
            update.setId(existing.getId());
            update.setChapterId(chapterId);
            update.setState(state);
            update.setUpdatedAt(new Date());
            this.updateById(update);
        }
    }
}
