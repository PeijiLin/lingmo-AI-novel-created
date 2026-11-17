package com.linpj.novel.create.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linpj.novel.create.pojo.dto.AuthorInfoUpdateRequest;
import com.linpj.novel.create.pojo.entity.AuthorInfo;
import com.linpj.novel.create.pojo.vo.AuthorProfileVo;

/**
* @author HL
* @description 针对表【author_info(作者信息)】的数据库操作Service
* @createDate 2025-09-14 17:11:42
*/
public interface AuthorInfoService extends IService<AuthorInfo> {

    /**
     * 按 userId 解析作者档案（无档案时按需自动创建，笔名默认取用户名）
     * @param userId 用户 id
     * @param createIfAbsent 无档案时是否自动创建
     * @return 作者档案；createIfAbsent 为 false 且不存在时返回 null
     */
    AuthorInfo resolveByUserId(Long userId, boolean createIfAbsent);

    /**
     * 查询当前登录用户的作者档案（个人主页）
     * @return 作者档案；尚未开档时返回 null
     */
    AuthorProfileVo getMyAuthorProfile();

    /**
     * 更新（或首次创建）当前登录用户的作者档案
     * @param request 笔名必填，其余字段可空（空表示清空）
     * @return 保存后的档案
     */
    AuthorProfileVo updateMyAuthorProfile(AuthorInfoUpdateRequest request);
}
