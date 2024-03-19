package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    public static final String FOLLOWS_KEY = "follows:";
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IUserService userService;

    @Override
    // save the follow information to db, and maintain a user-follow set in redis for sack of find common follow
    public Result followUser(Long followId, Boolean follow) {
        // get the current user
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        // update the db
        String key = FOLLOWS_KEY + userId;
        if (follow) {
            Follow f = new Follow();
            f.setUserId(userId);
            f.setFollowUserId(followId);
            boolean save = save(f);
            // save the follow information to redis
            if (save) {
                stringRedisTemplate.opsForSet().add(key, String.valueOf(followId));
            }
        } else {
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followId));
            // delete in redis
            if (remove) {
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(followId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollowed(Long followId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollow(Long userId) {
        UserDTO my = UserHolder.getUser();
        Long myId = my.getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(FOLLOWS_KEY + userId, FOLLOWS_KEY + myId);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> dtos = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(dtos);
    }
}
