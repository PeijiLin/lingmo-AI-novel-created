package com.linpj.novel.create.service;

import com.linpj.novel.create.pojo.entity.CharacterCards;
import com.linpj.novel.create.pojo.dto.CharacterCardsAddRequest;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author HL
* @description 针对表【character_cards】的数据库操作Service
* @createDate 2025-10-16 08:42:30
*/
public interface CharacterCardsService extends IService<CharacterCards> {
    /**
     * 创建角色卡
     */
    CharacterCardsVo createCharacterCard(CharacterCardsAddRequest card);

    /**
     * 更新角色卡
     */
    CharacterCards updateCharacterCard(CharacterCards updatedCard);

    /**
     * 删除角色卡
     */
    void deleteCharacterCard(Long id);

    /**
     * 获取单个角色卡
     */
    CharacterCardsVo getCharacterCardById(Long id);

    /**
     * 根据项目ID获取所有角色卡
     */
    List<CharacterCardsVo> getCharacterCardsByProjectId(Long bookId);

    /**
     * 根据项目ID和角色名获取角色卡
     */
    CharacterCardsVo getCharacterCardByName(Long bookId, String name);

    /**
     * 模糊搜索角色
     */
    List<CharacterCardsVo> searchCharacterCards(Long bookId, String name);
}