package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识库素材来源（WIKI_RAG_UNIFIED_PLAN §3.2：一条"素材来源"）
 * 上传的文件 / 手工录入的文本各成一条；文本块权威记录见 novel_rag_chunk。
 *
 * @author HL
 */
@TableName(value = "novel_kb_source")
@Data
public class NovelKbSource implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属书籍（书级隔离主键域） */
    private Long bookId;

    /** 录入用户 */
    private Long userId;

    /** 素材名（手工文本默认截取首句/时间戳） */
    private String name;

    private String description;

    /** UPLOAD / MANUAL / URL */
    private String sourceKind;

    /** 原始文件名（同名重传=替换，见 uk_kb_source_file） */
    private String fileName;

    /** MinIO 对象路径（可空） */
    private String filePath;

    /** 内容 SHA-256（秒传/幂等校验） */
    private String checksum;

    /** PARSING / READY / FAILED */
    private String status;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
