package com.linpj.novel.create.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.pojo.entity.AuthorInfo;
import com.linpj.novel.create.service.AuthorInfoService;
import com.linpj.novel.create.mapper.novel.AuthorInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author HL
* @description 针对表【author_info(作者信息)】的数据库操作Service实现
* @createDate 2025-09-14 17:11:42
*/
@Service
public class AuthorInfoServiceImpl extends ServiceImpl<AuthorInfoMapper, AuthorInfo>
    implements AuthorInfoService{

}




