package com.hmdp.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * query redis data with logical expire
     */
    public <ID, T> T queryWithLogicalExpire(String prefix, ID id, Class<T> tClass, Function<ID, T> dbCallBack) {
        // find cache
        String key = prefix + "::" + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json != null) {
            if (json.isEmpty()) { // case: not in db
                return null;
            }
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            T rst = JSONUtil.toBean((JSONObject) redisData.getData(), tClass);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return rst; // case: is not expired
            }
        }

        // case: expired or not in the redis
        // need to retrieve the data from db
        // create a new thread to get the value from db
        if (setLock(id)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                T result = dbCallBack.apply(id);
                if (result == null) {
                    // "" for preventing cache penetration
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                } else {
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop2Redis(result)));
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

        // case: return the expired data or not-in-cache data (regardless this data whether really in the db)
        if (json == null) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        return JSONUtil.toBean((JSONObject) redisData.getData(), tClass);
    }

    private <ID> boolean setLock(ID id) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent("lock:shop" + id, "1");
        return Boolean.TRUE.equals(flag);
    }

    private <ID> void unLock(ID id) {
        stringRedisTemplate.delete("lock:shop" + id);
    }

    private <T> RedisData shop2Redis(T shop) {
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(10));
        return redisData;
    }
}
