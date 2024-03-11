package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.CacheService;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    CacheService cacheService;

    @Resource
    CacheManager cacheManager;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Shop queryById(Long id) {
        // find cache
        String key = CACHE_SHOP_KEY + "::" + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (shopJson != null) {
            if (shopJson.isEmpty()) {
                return null;
            }
            RedisData shopRedis = JSONUtil.toBean(shopJson, RedisData.class);
            Shop shop = JSONUtil.toBean((JSONObject) shopRedis.getData(), Shop.class);
            if (shopRedis.getExpireTime().isAfter(LocalDateTime.now())) {
                return shop;
            }
        }

        // case: not in the cache or expired

        // create a new thread to get the value from db
        if (setLock(id)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                Shop shopById = getById(id);
                if (shopById == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                } else {
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop2Redis(shopById)));
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(id);
                }
            });
        }

        if (shopJson == null) {
            return null;
        }
        RedisData shopRedis = JSONUtil.toBean(shopJson, RedisData.class);
        return JSONUtil.toBean((JSONObject) shopRedis.getData(), Shop.class);
}

private RedisData shop2Redis(Shop shop) {
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(10));
    return redisData;
}

private boolean setLock(Long id) {
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent("lock:shop" + id, "1");
    return Boolean.TRUE.equals(flag);
}

private void unLock(Long id) {
    stringRedisTemplate.delete("lock:shop" + id);
}

@Override
public Result updateShop(Shop shop) {
    if (!updateById(shop)) {
        return Result.fail("update failed");
    }
    cacheService.evictShopCache(shop.getId());
    return Result.ok(shop);
}
}
