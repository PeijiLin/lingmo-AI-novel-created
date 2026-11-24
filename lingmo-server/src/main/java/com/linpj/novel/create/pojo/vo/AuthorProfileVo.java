package com.linpj.novel.create.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 作者档案（个人主页-创作身份展示用）
 * @author HL
 */
@Data
public class AuthorProfileVo implements Serializable {

    /**
     * 作者 id
     */
    private Long id;

    /**
     * 笔名
     */
    private String penName;

    /**
     * 0-男频 1-女频
     */
    private Integer workDirection;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 联系电话
     */
    private String telPhone;

    /**
     * 社交账号
     */
    private String chatAccount;

    /**
     * 邀请码
     */
    private String inviteCode;

    /**
     * 开档时间
     */
    private Date createTime;
}
