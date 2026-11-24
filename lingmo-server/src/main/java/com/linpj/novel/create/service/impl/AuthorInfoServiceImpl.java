package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.context.UserStatusContext;
import com.linpj.novel.create.exception.BusinessException;
import com.linpj.novel.create.exception.ErrorCode;
import com.linpj.novel.create.exception.ThrowsUtils;
import com.linpj.novel.create.mapper.novel.AuthorInfoMapper;
import com.linpj.novel.create.pojo.dto.AuthorInfoUpdateRequest;
import com.linpj.novel.create.pojo.entity.AuthorInfo;
import com.linpj.novel.create.pojo.entity.UserInfo;
import com.linpj.novel.create.pojo.vo.AuthorProfileVo;
import com.linpj.novel.create.service.AuthorInfoService;
import com.linpj.novel.create.service.UserInfoService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author HL
* @description 针对表【author_info(作者信息)】的数据库操作Service实现
* @createDate 2025-09-14 17:11:42
*/
@Service
public class AuthorInfoServiceImpl extends ServiceImpl<AuthorInfoMapper, AuthorInfo>
    implements AuthorInfoService {

    private final UserInfoService userInfoService;

    public AuthorInfoServiceImpl(UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @Override
    public AuthorInfo resolveByUserId(Long userId, boolean createIfAbsent) {
        ThrowsUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR);
        AuthorInfo authorInfo = this.getOne(
                new LambdaQueryWrapper<AuthorInfo>().eq(AuthorInfo::getUserId, userId));
        if (authorInfo == null && createIfAbsent) {
            UserInfo userInfo = userInfoService.getById(userId);
            authorInfo = new AuthorInfo();
            authorInfo.setUserId(userId);
            authorInfo.setPenName(userInfo != null && StringUtils.isNotBlank(userInfo.getUsername())
                    ? userInfo.getUsername() : "作者" + userId);
            authorInfo.setStatus(0);
            java.util.Date now = new java.util.Date();
            authorInfo.setCreateTime(now);
            authorInfo.setUpdateTime(now);
            boolean saved = this.save(authorInfo);
            ThrowsUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "作者创建失败");
        }
        return authorInfo;
    }

    @Override
    public AuthorProfileVo getMyAuthorProfile() {
        Long userId = UserStatusContext.getCurrentUserId();
        ThrowsUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        AuthorInfo authorInfo = resolveByUserId(userId, false);
        return authorInfo == null ? null : toProfileVo(authorInfo);
    }

    @Override
    public AuthorProfileVo updateMyAuthorProfile(AuthorInfoUpdateRequest request) {
        ThrowsUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long userId = UserStatusContext.getCurrentUserId();
        ThrowsUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "请先登录");

        // 笔名必填（唯一必填项）
        ThrowsUtils.throwIf(StringUtils.isBlank(request.getPenName()), ErrorCode.PARAMS_ERROR, "笔名不能为空");
        String penName = request.getPenName().trim();
        ThrowsUtils.throwIf(penName.length() < 1 || penName.length() > 20, ErrorCode.PARAMS_ERROR, "笔名长度须为 1-20 字");
        if (request.getWorkDirection() != null) {
            ThrowsUtils.throwIf(request.getWorkDirection() != 0 && request.getWorkDirection() != 1,
                    ErrorCode.PARAMS_ERROR, "创作方向取值仅支持 0-男频 / 1-女频");
        }

        // upsert：无档案则开档，有档案则覆写可编辑字段
        AuthorInfo authorInfo = resolveByUserId(userId, true);
        authorInfo.setPenName(penName);
        authorInfo.setWorkDirection(request.getWorkDirection());
        authorInfo.setEmail(StringUtils.trimToNull(request.getEmail()));
        authorInfo.setTelPhone(StringUtils.trimToNull(request.getTelPhone()));
        authorInfo.setChatAccount(StringUtils.trimToNull(request.getChatAccount()));
        authorInfo.setUpdateTime(new java.util.Date());
        boolean updated = this.updateById(authorInfo);
        ThrowsUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR, "作者档案保存失败");
        return toProfileVo(this.getById(authorInfo.getId()));
    }

    /**
     * 实体转作者档案 VO
     */
    private AuthorProfileVo toProfileVo(AuthorInfo authorInfo) {
        AuthorProfileVo vo = new AuthorProfileVo();
        vo.setId(authorInfo.getId());
        vo.setPenName(authorInfo.getPenName());
        vo.setWorkDirection(authorInfo.getWorkDirection());
        vo.setEmail(authorInfo.getEmail());
        vo.setTelPhone(authorInfo.getTelPhone());
        vo.setChatAccount(authorInfo.getChatAccount());
        vo.setInviteCode(authorInfo.getInviteCode());
        vo.setCreateTime(authorInfo.getCreateTime());
        return vo;
    }
}
