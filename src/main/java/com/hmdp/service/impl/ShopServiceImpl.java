package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.CacheService;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop queryById(Long id) {
        // cache
        String key = CACHE_SHOP_KEY + "::" + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (shopJson != null && shopJson.isEmpty()) {
            return null;
        }

        // database
        Shop shop = cacheService.getShop(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        return shop;
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
