package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * RAG 文本块权威记录（WIKI_RAG_UNIFIED_PLAN §3.2）
 *
 * <p>向量（vector_store）只是本表的索引投影：metadata 携带 chunkId 回指，
 * 换 embedding 模型或维度时按本表全量重建。
 *
 * @author HL
 */
@TableName(value = "novel_rag_chunk")
@Data
public class NovelRagChunk implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** 所属素材来源（novel_kb_source.id；章节回灌为空） */
    private Long sourceId;

    /** Wiki 正典页（M3；素材/章节块为空） */
    private Long pageId;

    /** Wiki 锚点（M3） */
    private Long anchorId;

    /** SETTING / CHAPTER / REFERENCE */
    private String chunkType;

    /** 章号（仅 CHAPTER，chapterNum 域） */
    private Integer chapterNum;

    /** 块序号（source 内或 book+chapter 内递增） */
    private Integer seq;

    private String content;

    /** 估算 token 数（中文 1 字 ≈ 1 token 折算） */
    private Integer tokenCount;

    /** 生成向量所用模型（重建时识别脏向量） */
    private String embeddingModel;

    /** READY / STALE */
    private String status;

    @TableField("created_at")
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
