package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;

import com.linpj.novel.create.utils.JsonbTypeHandler;
import lombok.Data;

/**
 * 
 * @TableName chat_history
 */
@TableName(value ="chat_history")
@Data
public class ChatHistory implements Serializable {
    /**
     * 
     */
    @TableId
    private Long id;

    /**
     * 
     */
    private Long bookId;

    /**
     * 
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private Object blockDataJsonb;

    /**
     * 
     */
    private Integer messageCount;

    /**
     * 
     */
    private Date startTimestamp;

    /**
     * 
     */
    private Date endTimestamp;

    /**
     * 
     */
    private Date createdAt;

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
        ChatHistory other = (ChatHistory) that;
        return (this.getId() == null ? other.getId() == null : this.getId().equals(other.getId()))
            && (this.getBookId() == null ? other.getBookId() == null : this.getBookId().equals(other.getBookId()))
            && (this.getBlockDataJsonb() == null ? other.getBlockDataJsonb() == null : this.getBlockDataJsonb().equals(other.getBlockDataJsonb()))
            && (this.getMessageCount() == null ? other.getMessageCount() == null : this.getMessageCount().equals(other.getMessageCount()))
            && (this.getStartTimestamp() == null ? other.getStartTimestamp() == null : this.getStartTimestamp().equals(other.getStartTimestamp()))
            && (this.getEndTimestamp() == null ? other.getEndTimestamp() == null : this.getEndTimestamp().equals(other.getEndTimestamp()))
            && (this.getCreatedAt() == null ? other.getCreatedAt() == null : this.getCreatedAt().equals(other.getCreatedAt()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getId() == null) ? 0 : getId().hashCode());
        result = prime * result + ((getBookId() == null) ? 0 : getBookId().hashCode());
        result = prime * result + ((getBlockDataJsonb() == null) ? 0 : getBlockDataJsonb().hashCode());
        result = prime * result + ((getMessageCount() == null) ? 0 : getMessageCount().hashCode());
        result = prime * result + ((getStartTimestamp() == null) ? 0 : getStartTimestamp().hashCode());
        result = prime * result + ((getEndTimestamp() == null) ? 0 : getEndTimestamp().hashCode());
        result = prime * result + ((getCreatedAt() == null) ? 0 : getCreatedAt().hashCode());
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
        sb.append(", blockDataJsonb=").append(blockDataJsonb);
        sb.append(", messageCount=").append(messageCount);
        sb.append(", startTimestamp=").append(startTimestamp);
        sb.append(", endTimestamp=").append(endTimestamp);
        sb.append(", createdAt=").append(createdAt);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}