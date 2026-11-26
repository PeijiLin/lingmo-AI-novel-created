package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Wiki 别名（§3.1：实体定位不走相似度）
 *
 * <p>normalized 为归一化形式（去空白/统一全半角），书内唯一——
 * "张三 / 三哥 / 张师兄" 对应同一 page，检索路由的实体定位入口。
 *
 * @author HL
 */
@TableName(value = "novel_wiki_alias")
@Data
public class NovelWikiAlias implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    private Long pageId;

    /** 原始别名（保留大小写与空格，展示用） */
    private String alias;

    /** 归一化形式（书内唯一键） */
    private String normalized;

    @TableField("created_at")
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
