package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    public static final int COUNT_BITS = 31;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    public long nextID(String prefix) {
        // get timestamp
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long beginSecond = 1710168161L;
        long timeStamp = nowSecond - beginSecond;

        // get date
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("incr:" + prefix + "::" + date);

        return timeStamp << COUNT_BITS | count;
    }
}