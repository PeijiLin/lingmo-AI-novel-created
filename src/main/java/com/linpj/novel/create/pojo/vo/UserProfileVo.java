package com.linpj.novel.create.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户个人资料（个人主页展示用，不含密码）
 * @author HL
 */
@Data
public class UserProfileVo implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像图片URL
     */
    private String userPhoto;

    /**
     * 0-男 1-女
     */
    private Integer userSex;

    /**
     * 账户余额
     */
    private Long accountBalance;

    /**
     * 注册时间
     */
    private LocalDateTime createTime;
}
