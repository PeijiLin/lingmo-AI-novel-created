package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.entity.NovelCharacterState;

import java.util.List;

/**
 * 人物动态状态服务（角色卡是静态设定，本表是随剧情推进的当前态）
 *
 * 维护时机（设计 5.3）：章节定稿后由抽取节点增量更新（阶段3同步实现，阶段4挪入消费者）；
 * 读取时机：上下文组装时仅加载出场角色的当前状态。
 *
 * @author HL
 */
public interface CharacterStateService extends IService<NovelCharacterState> {

    /** 渲染指定角色的当前状态为 prompt 文本（characterIds 为空则渲染全部有状态的角色） */
    String renderStates(Long bookId, List<Long> characterIds);

    /**
     * 覆盖式更新某角色的当前状态（state 为自由结构：位置/心态/新获知信息/持有物品/受伤变化）
     */
    void updateState(Long bookId, Long characterId, Long chapterId, Object state);
}
