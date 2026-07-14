package com.linpj.novel.create.pojo.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author HL
 */
@Data
public class BookInfoListVo implements Serializable {

    private Long id;

    /**
     * 0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 封面图片URL
     */
    private String picUrl;

    /**
     * 书籍名称
     */
    private String bookName;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 书籍描述
     */
    private String bookDesc;

    /**
     * 总分:10，真实评分 = score/10
     */
    private Integer score;

    /**
     * 0-连载中 1-已完结
     */
    private Integer bookStatus;

    /**
     * 访问次数
     */
    private Long visitCount;

    /**
     * 字数统计
     */
    private Integer wordCount;

    /**
     * 评论数量
     */
    private Integer commentCount;

    /**
     * 最新章节ID
     */
    private Long lastChapterId;

    /**
     * 最新章节名称
     */
    private String lastChapterName;

    /**
     * 最新章节更新时间
     */
    private Date lastChapterUpdateTime;

    /**
     * 1-收费 0-免费
     */
    private Integer isVip;

    /**
     * 创建时间
     */
    private Date createTime;

    private List<String> tags;

}
