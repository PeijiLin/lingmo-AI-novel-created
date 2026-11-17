package com.linpj.novel.create.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 作者档案更新请求（个人主页编辑 / 首次开档）
 * @author HL
 */
@Data
public class AuthorInfoUpdateRequest implements Serializable {

    /**
     * 笔名（必填，1-20 字）
     */
    private String penName;

    /**
     * 0-男频 1-女频（可空）
     */
    private Integer workDirection;

    /**
     * 电子邮箱（可空）
     */
    private String email;

    /**
     * 联系电话（可空）
     */
    private String telPhone;

    /**
     * 社交账号（可空）
     */
    private String chatAccount;
}
