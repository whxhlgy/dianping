package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.CacheService;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.Serializable;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

    @Override
    @Cacheable(value = CACHE_SHOP_KEY, key = "#id", unless = "#result == null")
    public Shop getById(Serializable id) {
        return super.getById(id);
    }

    @Override
//    @Transactional // not work !
    public Result updateShop(Shop shop) {
        if (!updateById(shop)) {
            return Result.fail("update failed");
        }
        cacheService.evictShopCache(shop.getId());
        return Result.ok(shop);
    }
}
