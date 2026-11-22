package com.linpj.novel.create.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 摘要树节点（v2.0 设计 6.2 SummaryTree / M5-B4：定长摘要树，H2 记忆分层的数据机制本体）
 *
 * 三层：CHAPTER（章节详摘 ≤500 字）/ SEGMENT（区段中摘，每 10 章一节点 ≤600 字）/ BOOK（全书短摘 ≤300 字）。
 * 上层由下层重算；定长由代码截断强制——摘要总量恒定，不随章数线性膨胀（R-02：滚动摘要 30+ 章后信息稀释）。
 *
 * @author HL
 */
@TableName(value = "novel_summary_tree")
@Data
public class NovelSummaryTree implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookId;

    /** CHAPTER / SEGMENT / BOOK */
    private String level;

    /** CHAPTER=chapter_num；SEGMENT=区段序号（0 起）；BOOK=0 */
    @SuppressWarnings("java:S116")
    private Integer seq;

    /** 章节记录 ID（CHAPTER 层溯源用；其余层为 null） */
    private Long chapterId;

    /** 摘要内容（代码截断保障定长） */
    private String content;

    /** 血缘标记：下层写入时置上层 stale，重算清除（渲染始终取最新内容，近因性优先） */
    private Boolean isStale;

    /** 更新版本（每次重算 +1，幂等重建可对账） */
    private Integer version;

    private Date updatedAt;
}
