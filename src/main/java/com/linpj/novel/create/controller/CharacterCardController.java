package com.linpj.novel.create.controller;

import com.linpj.novel.create.constant.BaseResponse;
import com.linpj.novel.create.constant.ResultUtils;
import com.linpj.novel.create.pojo.dto.CharacterCardsAddRequest;
import com.linpj.novel.create.pojo.entity.CharacterCards;
import com.linpj.novel.create.pojo.vo.CharacterCardsVo;
import com.linpj.novel.create.service.CharacterCardsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色卡片接口
 */
@RestController
@RequestMapping("/api/character-card")
public class CharacterCardController {

    @Autowired
    private CharacterCardsService characterCardService;

    /**
     * 创建角色卡
     */
    @PostMapping("/add")
    public BaseResponse<CharacterCardsVo> createCharacterCard(
            @RequestBody CharacterCardsAddRequest characterCard) {
        System.out.println(characterCard);
        CharacterCardsVo savedCard = characterCardService.createCharacterCard(characterCard);
        return ResultUtils.success(savedCard);
    }

    /**
     * 更新角色卡
     */
    @PutMapping("/modify")
    public BaseResponse<CharacterCards> updateCharacterCard(@RequestBody CharacterCards characterCard) {
        CharacterCards updatedCard = characterCardService.updateCharacterCard(characterCard);
        return ResultUtils.success(updatedCard);
    }

    /**
     * 删除角色卡
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteCharacterCard(@PathVariable Long id) {
        characterCardService.deleteCharacterCard(id);
        return ResultUtils.success(true);
    }

    /**
     * 获取单个角色卡
     */
    @GetMapping("/{id}")
    public BaseResponse<CharacterCardsVo> getCharacterCard(@PathVariable Long id) {
        CharacterCardsVo card = characterCardService.getCharacterCardById(id);
        return ResultUtils.success(card);
    }

    /**
     * 获取项目所有角色卡
     */
    @GetMapping("/project/{bookId}")
    public BaseResponse<List<CharacterCardsVo>> getCharacterCards(@PathVariable Long bookId) {
        List<CharacterCardsVo> cards = characterCardService.getCharacterCardsByProjectId(bookId);
        return ResultUtils.success(cards);
    }

    /**
     * 根据角色名获取角色卡
     */
    @GetMapping("/project/{bookId}/name/{name}")
    public BaseResponse<CharacterCardsVo> getCharacterCardByName(
            @PathVariable Long bookId,
            @PathVariable String name) {
        CharacterCardsVo card = characterCardService.getCharacterCardByName(bookId, name);
        return ResultUtils.success(card);
    }

    /**
     * 搜索角色卡（模糊匹配）
     */
    @GetMapping("/project/{bookId}/search")
    public BaseResponse<List<CharacterCardsVo>> searchCharacterCards(
            @PathVariable Long bookId,
            @RequestParam String name) {
        List<CharacterCardsVo> cards = characterCardService.searchCharacterCards(bookId, name);
        return ResultUtils.success(cards);
    }
}