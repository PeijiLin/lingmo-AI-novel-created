package com.linpj.novel.create.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linpj.novel.create.constant.CacheConsts;
import com.linpj.novel.create.mapper.novel.BookCategoryMapper;
import com.linpj.novel.create.pojo.entity.BookCategory;
import com.linpj.novel.create.pojo.vo.BookCategoryVo;
import com.linpj.novel.create.service.BookCategoryService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
* @author HL
* @description 针对表【book_category(小说类别)】的数据库操作Service实现
* @createDate 2025-09-09 19:56:18
*/
@Service
public class BookCategoryServiceImpl extends ServiceImpl<BookCategoryMapper, BookCategory>
    implements BookCategoryService {

    private final RedisTemplate<String, Object> redisTemplate;

    public BookCategoryServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Map<String, List<BookCategoryVo>> queryBookCategories() {
        String categoryKey = CacheConsts.REDIS_CACHE_PREFIX + "categories";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(categoryKey))) {
            Object o = redisTemplate.opsForValue().get(categoryKey);
            return (Map<String, List<BookCategoryVo>>) o;
        }
        List<BookCategoryVo> list = this.list().stream().map(category -> {
            BookCategoryVo vo = new BookCategoryVo();
            vo.setId(category.getId());
            vo.setWorkDirection(category.getWorkDirection());
            vo.setCategoryName(category.getName());
            return vo;
        }).toList();
        Map<String, List<BookCategoryVo>> map = list.stream()
                .filter(vo -> vo.getWorkDirection() != null && !vo.getWorkDirection().isBlank())
                .collect(Collectors.groupingBy(BookCategoryVo::getWorkDirection));
        redisTemplate.opsForValue().set(categoryKey, map);
        return map;
    }
}




