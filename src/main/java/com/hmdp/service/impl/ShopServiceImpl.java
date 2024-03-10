package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.CacheService;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    @Override
    public Shop queryById(Long id) {
        // find cache
        String key = CACHE_SHOP_KEY + "::" + id;
        String shopStr = stringRedisTemplate.opsForValue().get(key);
        Shop shop = JSONUtil.toBean(shopStr, Shop.class);

        if (shopStr != null) {
            if (shopStr.isEmpty()) {
                return null;
            }
            return shop;
        }

        // get the value from db, but not all threads
        try {
            if (!setLock(id)) {
                Thread.sleep(50);
                return queryById(id);
            }

            shop = getById(id);
//            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(id);
        }

        // update to cache
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        } else {
            cacheService.updateShopCache(shop);
        }
        return shop;
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
