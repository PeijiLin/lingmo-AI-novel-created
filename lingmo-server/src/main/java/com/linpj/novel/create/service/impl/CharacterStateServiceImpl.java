package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.mapper.novel.NovelCharacterStateMapper;
import com.linpj.novel.create.pojo.entity.NovelCharacterState;
import com.linpj.novel.create.pojo.pipeline.AliveStatus;
import com.linpj.novel.create.service.CharacterStateService;
import com.linpj.novel.create.utils.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 人物动态状态服务实现
 *
 * @author HL
 */
@Service
public class CharacterStateServiceImpl extends ServiceImpl<NovelCharacterStateMapper, NovelCharacterState>
        implements CharacterStateService {

    private static final Logger log = LoggerFactory.getLogger(CharacterStateServiceImpl.class);

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
                    .append(JsonUtil.toJson(stripAdminFields(state.getState()))).append("\n");
        }
        return sb.toString();
    }

    /**
     * 剥离提示词不应暴露的管理字段（如抽取置信度 confidence）。
     * 置信度仅用于账本审计与阈值门禁（设计 2.5），不进入创作上下文。
     * aliveStatus/itemNames 为 in-world 事实，保留在提示词中（Writer 需知道角色已死亡，避免写出死人活动）。
     */
    private static Object stripAdminFields(Object state) {
        if (state instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null && !k.equals("confidence")) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        return state;
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

    // ------------------------------------------------------------------
    // 硬字段类型化读取（B5：RC-01/RC-06 消费端，旧数据容错）
    // ------------------------------------------------------------------

    @Override
    public Map<Long, AliveStatus> findAliveStatuses(Long bookId, List<Long> characterIds) {
        Map<Long, Object> states = loadStatesAsMap(bookId, characterIds);
        Map<Long, AliveStatus> result = new LinkedHashMap<>();
        states.forEach((characterId, state) ->
                result.put(characterId, AliveStatus.parse(fieldOf(state, "aliveStatus"))));
        return result;
    }

    @Override
    public Map<Long, Set<String>> findHeldItemNames(Long bookId, List<Long> characterIds) {
        Map<Long, Object> states = loadStatesAsMap(bookId, characterIds);
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        states.forEach((characterId, state) -> {
            Set<String> items = new LinkedHashSet<>();
            Object raw = fieldOf(state, "itemNames");
            if (raw instanceof List<?> list) {
                list.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(items::add);
            }
            result.put(characterId, items);
        });
        return result;
    }

    /** 按角色定位批量加载 state 原始对象（book+character 唯一，无重复行问题） */
    private Map<Long, Object> loadStatesAsMap(Long bookId, List<Long> characterIds) {
        Map<Long, Object> result = new HashMap<>();
        if (bookId == null || characterIds == null || characterIds.isEmpty()) {
            return result;
        }
        List<NovelCharacterState> states = this.list(new LambdaQueryWrapper<NovelCharacterState>()
                .eq(NovelCharacterState::getBookId, bookId)
                .in(NovelCharacterState::getCharacterId, characterIds));
        states.forEach(state -> result.put(state.getCharacterId(), state.getState()));
        return result;
    }

    @Override
    public Map<Long, LocationFact> findLocations(Long bookId, List<Long> characterIds) {
        Map<Long, LocationFact> result = new LinkedHashMap<>();
        if (bookId == null || characterIds == null || characterIds.isEmpty()) {
            return result;
        }
        List<NovelCharacterState> rows = this.list(new LambdaQueryWrapper<NovelCharacterState>()
                .eq(NovelCharacterState::getBookId, bookId)
                .in(NovelCharacterState::getCharacterId, characterIds));
        rows.forEach(row -> {
            Object raw = fieldOf(row.getState(), "location");
            String location = raw == null ? null : String.valueOf(raw).trim();
            result.put(row.getCharacterId(), new LocationFact(
                    location == null || location.isEmpty() ? null : location, row.getChapterId()));
        });
        return result;
    }

    /** 从 free-form state（Jackson 反序列化为 Map）取字段；非 Map 结构返回 null（旧数据兼容） */
    private static Object fieldOf(Object state, String field) {
        if (state instanceof Map<?, ?> map) {
            return map.get(field);
        }
        return null;
    }
}
