package com.linpj.novel.create.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户资料更新请求（个人主页编辑）
 * @author HL
 */
@Data
public class UserProfileUpdateRequest implements Serializable {

    /**
     * 昵称（1-20 字，传 null 表示不变更）
     */
    private String nickName;

    /**
     * 0-男 1-女（传 null 表示不变更）
     */
    private Integer userSex;
}
