package com.linpj.novel.create.pojo.dto;

import com.baomidou.mybatisplus.annotation.TableId;
import com.linpj.novel.create.pojo.entity.CharacterRelationships;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
public class CharacterCardsAddRequest implements Serializable {

    /**
     * 所属书籍ID
     */
    private Long bookId;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 角色性格特征
     */
    private String personality;

    /**
     * 角色背景故事
     */
    private String background;

    /**
     * 角色外貌特征
     */
    private String appearance;

    /**
     * 角色年龄
     */
    private Integer age;

    /**
     * 角色性别
     */
    private String gender;

    /**
     * 角色职业
     */
    private String occupation;

    /**
     * 角色能力
     */
    private String abilities;

    /**
     * 角色弱点
     */
    private String weaknesses;

    /**
     * 角色目标
     */
    private String goals;

    /**
     * 角色关系网络
     */
    private List<CharacterRelationships> relationships;
}