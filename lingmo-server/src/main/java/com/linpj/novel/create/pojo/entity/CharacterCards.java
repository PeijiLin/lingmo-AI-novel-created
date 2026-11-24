package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linpj.novel.create.utils.JsonbTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 角色卡片表
 * @TableName character_cards
 */
@TableName(value ="character_cards", autoResultMap = true)
@Data
public class CharacterCards implements Serializable {
    /**
     * 角色卡片唯一标识符
     */
    @TableId
    private Long id;

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
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object relationships;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        CharacterCards other = (CharacterCards) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getBookId() == null ? other.getBookId() == null : this.getBookId().equals(other.getBookId()))
            && (this.getName() == null ? other.getName() == null : this.getName().equals(other.getName()))
            && (this.getDescription() == null ? other.getDescription() == null : this.getDescription().equals(other.getDescription()))
            && (this.getPersonality() == null ? other.getPersonality() == null : this.getPersonality().equals(other.getPersonality()))
            && (this.getBackground() == null ? other.getBackground() == null : this.getBackground().equals(other.getBackground()))
            && (this.getAppearance() == null ? other.getAppearance() == null : this.getAppearance().equals(other.getAppearance()))
            && (this.getAge() == null ? other.getAge() == null : this.getAge().equals(other.getAge()))
            && (this.getGender() == null ? other.getGender() == null : this.getGender().equals(other.getGender()))
            && (this.getOccupation() == null ? other.getOccupation() == null : this.getOccupation().equals(other.getOccupation()))
            && (this.getAbilities() == null ? other.getAbilities() == null : this.getAbilities().equals(other.getAbilities()))
            && (this.getWeaknesses() == null ? other.getWeaknesses() == null : this.getWeaknesses().equals(other.getWeaknesses()))
            && (this.getGoals() == null ? other.getGoals() == null : this.getGoals().equals(other.getGoals()))
            && (this.getRelationships() == null ? other.getRelationships() == null : this.getRelationships().equals(other.getRelationships()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getBookId() == null) ? 0 : getBookId().hashCode());
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getDescription() == null) ? 0 : getDescription().hashCode());
        result = prime * result + ((getPersonality() == null) ? 0 : getPersonality().hashCode());
        result = prime * result + ((getBackground() == null) ? 0 : getBackground().hashCode());
        result = prime * result + ((getAppearance() == null) ? 0 : getAppearance().hashCode());
        result = prime * result + ((getAge() == null) ? 0 : getAge().hashCode());
        result = prime * result + ((getGender() == null) ? 0 : getGender().hashCode());
        result = prime * result + ((getOccupation() == null) ? 0 : getOccupation().hashCode());
        result = prime * result + ((getAbilities() == null) ? 0 : getAbilities().hashCode());
        result = prime * result + ((getWeaknesses() == null) ? 0 : getWeaknesses().hashCode());
        result = prime * result + ((getGoals() == null) ? 0 : getGoals().hashCode());
        result = prime * result + ((getRelationships() == null) ? 0 : getRelationships().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", bookId=").append(bookId);
        sb.append(", name=").append(name);
        sb.append(", description=").append(description);
        sb.append(", personality=").append(personality);
        sb.append(", background=").append(background);
        sb.append(", appearance=").append(appearance);
        sb.append(", age=").append(age);
        sb.append(", gender=").append(gender);
        sb.append(", occupation=").append(occupation);
        sb.append(", abilities=").append(abilities);
        sb.append(", weaknesses=").append(weaknesses);
        sb.append(", goals=").append(goals);
        sb.append(", relationships=").append(relationships);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}