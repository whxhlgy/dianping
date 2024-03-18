package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    private static final String BLOG_LIKED_KEY = "blog:like:";

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("the blog not exist");
        }
        // find related user
        getUser(blog);
        getLiked(blog);
        return Result.ok(blog);
    }

    private void getLiked(Blog blog) {
        // get current user id
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + userId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            getUser(blog);
            getLiked(blog);
        });
        return Result.ok(records);
    }

    private void getUser(Blog blog) {
        // get the userid of blog owner
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long id) {
        // get the userId
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        // check if the user is liked the blog
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            // decremente the like number
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) {
                // delete the user from set
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            // increase the like number
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                // add user to redis set of all liked user
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok(id);
    }

    @Override
    public Result queryLikesWithOrder(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // the list of user id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<String> idsStrList = ids.stream().map(String::valueOf).collect(Collectors.toList());
        String joinStr = String.join(",", idsStrList);
        // find top5 user by id: where id IN (5, 1) ORDER BY FIELD (id, 5, 1)
        List<UserDTO> userDTOs = userService.query().in("id", ids).last("ORDER BY FIELD(id, " + joinStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }
}
