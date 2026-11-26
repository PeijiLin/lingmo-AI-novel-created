package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.linpj.novel.create.service.CharacterRelationshipsService;
import com.linpj.novel.create.mapper.novel.CharacterRelationshipsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
* @author HL
* @description 针对表【character_relationships】的数据库操作Service实现
* @createDate 2025-10-16 08:43:18
*/
@Service
public class CharacterRelationshipsServiceImpl extends ServiceImpl<CharacterRelationshipsMapper, CharacterRelationships>
    implements CharacterRelationshipsService {

    @Override
    public List<CharacterRelationships> listForBook(Long bookId) {
        if (bookId == null) {
            return List.of();
        }
        return this.list(new LambdaQueryWrapper<CharacterRelationships>()
                .eq(CharacterRelationships::getBookId, bookId));
    }

    @Override
    public List<CharacterRelationships> listActive(Long bookId, Integer chapterNum) {
        return listForBook(bookId).stream()
                .filter(edge -> activeAt(edge, chapterNum))
                .toList();
    }

    /**
     * 生效区间判定（闭区间；null 视为开边界——与 novel_state_fact / RC-08 同语义）。
     * 章号不可得时只认"长期有效"边，宁缺勿错。
     */
    static boolean activeAt(CharacterRelationships edge, Integer chapterNum) {
        if (edge == null) {
            return false;
        }
        boolean hasInterval = edge.getChapterFrom() != null || edge.getChapterTo() != null;
        if (chapterNum == null) {
            return !hasInterval;
        }
        boolean afterFrom = edge.getChapterFrom() == null || chapterNum >= edge.getChapterFrom();
        boolean beforeTo = edge.getChapterTo() == null || chapterNum <= edge.getChapterTo();
        return afterFrom && beforeTo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceOutgoing(Long bookId, Long characterId, List<CharacterRelationships> edges) {
        if (bookId == null || characterId == null) {
            return;
        }
        this.remove(new LambdaQueryWrapper<CharacterRelationships>()
                .eq(CharacterRelationships::getBookId, bookId)
                .eq(CharacterRelationships::getFromCharacterId, characterId));
        if (edges == null || edges.isEmpty()) {
            return;
        }
        List<CharacterRelationships> rows = new ArrayList<>();
        for (CharacterRelationships edge : edges) {
            if (edge == null || edge.getToCharacterId() == null) {
                continue;
            }
            CharacterRelationships row = new CharacterRelationships();
            row.setBookId(bookId);
            row.setFromCharacterId(characterId);
            row.setToCharacterId(edge.getToCharacterId());
            row.setRelationshipType(edge.getRelationshipType());
            row.setDescription(edge.getDescription());
            row.setChapterFrom(edge.getChapterFrom());
            row.setChapterTo(edge.getChapterTo());
            row.setIntensity(edge.getIntensity());
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            this.saveBatch(rows);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByCharacter(Long bookId, Long characterId) {
        if (bookId == null || characterId == null) {
            return;
        }
        this.remove(new LambdaQueryWrapper<CharacterRelationships>()
                .eq(CharacterRelationships::getBookId, bookId)
                .and(w -> w.eq(CharacterRelationships::getFromCharacterId, characterId)
                        .or()
                        .eq(CharacterRelationships::getToCharacterId, characterId)));
    }
}
