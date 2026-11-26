package com.linpj.novel.create.service;

import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author HL
* @description 针对表【character_relationships】的数据库操作Service
* @createDate 2025-10-16 08:43:18
*/
public interface CharacterRelationshipsService extends IService<CharacterRelationships> {

    /** 本书全部关系边（含历史生效区间） */
    List<CharacterRelationships> listForBook(Long bookId);

    /**
     * 按章号取当前生效的关系边。
     * 章号为空时只返回"长期有效"边（无 chapter_from / chapter_to），避免把已失效关系喂给模型。
     *
     * @param bookId     书籍 ID
     * @param chapterNum 当前章号（book_chapter.chapter_num 域，可空）
     */
    List<CharacterRelationships> listActive(Long bookId, Integer chapterNum);

    /** 以角色卡为写入口：替换该角色的出边（旧行物理删除，关系主体是角色卡而非历史账本） */
    void replaceOutgoing(Long bookId, Long characterId, List<CharacterRelationships> edges);

    /** 角色删除时清理其全部关系边（出边与入边） */
    void deleteByCharacter(Long bookId, Long characterId);
}
