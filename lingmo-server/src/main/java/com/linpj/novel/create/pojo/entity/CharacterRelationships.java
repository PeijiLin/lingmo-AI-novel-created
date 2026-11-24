package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName character_relationships
 */
@TableName(value ="character_relationships")
@Data
public class CharacterRelationships implements Serializable {
    /**
     * 关系唯一标识符
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属书籍ID
     */
    private Long bookId;

    /**
     * 起始角色ID
     */
    private Long fromCharacterId;

    /**
     * 目标角色ID
     */
    private Long toCharacterId;

    /**
     * 关系类型
     */
    private String relationshipType;

    /**
     * 关系描述
     */
    private String description;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 关系生效起始章号（chapterNum 域；B1-1：关系随剧情演化——旧行软失效不物理删除）
     */
    private Integer chapterFrom;

    /**
     * 关系失效章号（chapterNum 域，null = 当前有效；与 chapter_from 均空 = 长期有效，存量数据兼容）
     */
    private Integer chapterTo;

    /**
     * 关系强度/张力标记（0-9；供 RC-08 称谓校验与后续张力曲线分析，可空）
     */
    private Integer intensity;

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
        CharacterRelationships other = (CharacterRelationships) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getBookId() == null ? other.getBookId() == null : this.getBookId().equals(other.getBookId()))
            && (this.getFromCharacterId() == null ? other.getFromCharacterId() == null : this.getFromCharacterId().equals(other.getFromCharacterId()))
            && (this.getToCharacterId() == null ? other.getToCharacterId() == null : this.getToCharacterId().equals(other.getToCharacterId()))
            && (this.getRelationshipType() == null ? other.getRelationshipType() == null : this.getRelationshipType().equals(other.getRelationshipType()))
            && (this.getDescription() == null ? other.getDescription() == null : this.getDescription().equals(other.getDescription()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()))
            && (this.getUpdatedAt() == null ? other.getUpdatedAt() == null : this.getUpdatedAt().equals(other.getUpdatedAt()))
            && (this.getChapterFrom() == null ? other.getChapterFrom() == null : this.getChapterFrom().equals(other.getChapterFrom()))
            && (this.getChapterTo() == null ? other.getChapterTo() == null : this.getChapterTo().equals(other.getChapterTo()))
            && (this.getIntensity() == null ? other.getIntensity() == null : this.getIntensity().equals(other.getIntensity()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getBookId() == null) ? 0 : getBookId().hashCode());
        result = prime * result + ((getFromCharacterId() == null) ? 0 : getFromCharacterId().hashCode());
        result = prime * result + ((getToCharacterId() == null) ? 0 : getToCharacterId().hashCode());
        result = prime * result + ((getRelationshipType() == null) ? 0 : getRelationshipType().hashCode());
        result = prime * result + ((getDescription() == null) ? 0 : getDescription().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
        result = prime * result + ((getUpdatedAt() == null) ? 0 : getUpdatedAt().hashCode());
        result = prime * result + ((getChapterFrom() == null) ? 0 : getChapterFrom().hashCode());
        result = prime * result + ((getChapterTo() == null) ? 0 : getChapterTo().hashCode());
        result = prime * result + ((getIntensity() == null) ? 0 : getIntensity().hashCode());
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
        sb.append(", fromCharacterId=").append(fromCharacterId);
        sb.append(", toCharacterId=").append(toCharacterId);
        sb.append(", relationshipType=").append(relationshipType);
        sb.append(", description=").append(description);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", updatedAt=").append(updatedAt);
        sb.append(", chapterFrom=").append(chapterFrom);
        sb.append(", chapterTo=").append(chapterTo);
        sb.append(", intensity=").append(intensity);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}