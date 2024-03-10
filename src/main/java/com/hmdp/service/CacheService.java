package com.hmdp.service;

import com.hmdp.entity.Shop;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;


@Service
public class CacheService {
    @Resource
    IShopService shopService;

    @Cacheable(value = CACHE_SHOP_KEY, key = "#id", unless = "#result == null")
    public Shop getShop(Long id) {
        return shopService.getById(id);
    }

    @CachePut(value = CACHE_SHOP_KEY, key = "#shop.id", unless = "#result == null")
    public Shop updateShopCache(Shop shop) {
        return shop;
    }


    @CacheEvict(value = CACHE_SHOP_KEY, key = "#id")
    public void evictShopCache(Long id) {
        // No need to implement anything here, the annotation takes care of the eviction
    }
}
