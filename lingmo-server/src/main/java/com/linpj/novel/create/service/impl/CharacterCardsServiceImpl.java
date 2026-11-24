package com.linpj.novel.create.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.reflect.TypeToken;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.pojo.dto.CharacterCardsAddRequest;
import com.linpj.novel.create.pojo.entity.CharacterCards;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import com.linpj.novel.create.mapper.novel.CharacterCardsMapper;
import com.linpj.novel.create.utils.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.util.List;

/**
* @author HL
* @description 针对表【character_cards】的数据库操作Service实现
* @createDate 2025-10-16 08:42:30
*/
@Service
public class CharacterCardsServiceImpl extends ServiceImpl<CharacterCardsMapper, CharacterCards>
    implements CharacterCardsService {

    private final CharacterCardsMapper characterCardsMapper;

    public CharacterCardsServiceImpl(CharacterCardsMapper characterCardsMapper) {
        this.characterCardsMapper = characterCardsMapper;
    }

    /**
     * 创建角色卡
     */
    @Transactional(rollbackFor = Exception.class)
    public CharacterCardsVo createCharacterCard(CharacterCardsAddRequest card) {
        // 检查项目内角色名是否唯一
        ThrowsUtils.throwIf(card == null, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(card.getBookId() == null || card.getBookId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowsUtils.throwIf(StringUtils.isBlank(card.getName()), ErrorCode.PARAMS_ERROR, "角色名不能为空");
        
        CharacterCards existingCard = this.getOne(new LambdaQueryWrapper<CharacterCards>()
                .eq(CharacterCards::getBookId, card.getBookId())
                .eq(CharacterCards::getName, card.getName()));
        if (existingCard != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该角色已存在");
        }
        CharacterCards characterCards = BeanUtil.copyProperties(card, CharacterCards.class);
        characterCards.setRelationships(JsonUtil.toJson(card.getRelationships()));
        System.out.println(characterCards);
        boolean save = this.save(characterCards);
//        characterCardsMapper.save(characterCards);
        return toVo(characterCards);
    }

    /**
     * 更新角色卡
     */
    @Transactional(rollbackFor = Exception.class)
    public CharacterCards updateCharacterCard(CharacterCards updatedCard) {
        ThrowsUtils.throwIf(updatedCard == null || updatedCard.getId() == null || updatedCard.getId() <= 0, 
                ErrorCode.PARAMS_ERROR, "无效的角色卡ID");
        
        CharacterCards characterCards = this.getById(updatedCard.getId());
        if (characterCards == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean b = this.updateById(updatedCard);
        ThrowsUtils.throwIf(!b, ErrorCode.SYSTEM_ERROR);
        return this.getById(updatedCard.getId());
    }

    /**
     * 删除角色卡
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCharacterCard(Long id) {
        ThrowsUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "无效的角色卡ID");
        
        CharacterCards characterCards = this.getById(id);
        if (characterCards == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除数据不存在");
        }
        boolean b = this.removeById(id);
        ThrowsUtils.throwIf(!b, ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 获取单个角色卡
     */
    @Transactional(readOnly = true)
    public CharacterCardsVo getCharacterCardById(Long id) {
        ThrowsUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "无效的角色卡ID");
        
        CharacterCards characterCards = this.getById(id);
        if (characterCards == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return toVo(characterCards);
    }

    /**
     * 根据项目ID获取所有角色卡
     */
    @Transactional(readOnly = true)
    public List<CharacterCardsVo> getCharacterCardsByProjectId(Long bookId) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        
        List<CharacterCards> list = this.list(new LambdaQueryWrapper<CharacterCards>().eq(CharacterCards::getBookId, bookId));
        return list.stream().map(this::toVo).toList();
    }

    /**
     * 根据项目ID和角色名获取角色卡
     */
    @Transactional(readOnly = true)
    public CharacterCardsVo getCharacterCardByName(Long bookId, String name) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        ThrowsUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR, "角色名不能为空");
        
        CharacterCards cards = this.getOne(new LambdaQueryWrapper<CharacterCards>()
                .eq(CharacterCards::getBookId, bookId)
                .eq(CharacterCards::getName, name));
        if (cards == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return toVo(cards);
    }

    /**
     * 模糊搜索角色
     */
    @Transactional(readOnly = true)
    public List<CharacterCardsVo> searchCharacterCards(Long bookId, String name) {
        ThrowsUtils.throwIf(bookId == null || bookId <= 0, ErrorCode.PARAMS_ERROR, "无效的书籍ID");
        ThrowsUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR, "搜索关键字不能为空");
        
        List<CharacterCards> list = this.list(new LambdaQueryWrapper<CharacterCards>()
                .eq(CharacterCards::getBookId, bookId)
                .like(CharacterCards::getName, name));
        return list.stream().map(this::toVo).toList();
    }

    private CharacterCardsVo toVo(CharacterCards characterCards) {
        if (characterCards == null) {
            return null;
        }
        
        CharacterCardsVo characterCardsVo = new CharacterCardsVo();
        characterCardsVo.setId(characterCards.getId());
        characterCardsVo.setBookId(characterCards.getBookId());
        characterCardsVo.setName(characterCards.getName());
        characterCardsVo.setDescription(characterCards.getDescription());
        characterCardsVo.setPersonality(characterCards.getPersonality());
        characterCardsVo.setBackground(characterCards.getBackground());
        characterCardsVo.setAppearance(characterCards.getAppearance());
        characterCardsVo.setAge(characterCards.getAge());
        characterCardsVo.setGender(characterCards.getGender());
        characterCardsVo.setOccupation(characterCards.getOccupation());
        characterCardsVo.setAbilities(characterCards.getAbilities());
        characterCardsVo.setWeaknesses(characterCards.getWeaknesses());
        characterCardsVo.setGoals(characterCards.getGoals());
        
        // 安全地处理relationships字段
        Object relationships = characterCards.getRelationships();
        if (relationships != null) {
            Type type = new TypeToken<>() {
            }.getType();
            characterCardsVo.setRelationships(
                JsonUtil.fromJson(relationships.toString(), type)
            );
        }
        characterCardsVo.setCreatedAt(characterCards.getCreatedAt());
        characterCardsVo.setUpdatedAt(characterCards.getUpdatedAt());
        return characterCardsVo;
    }
}