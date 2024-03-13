package com.hmdp.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.hmdp.service.ILock;

public class SimpleRedisLock implements ILock {
    
    private static final String LOCK_PREFIX = "lock::";
    private String name;
    private StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeOutSec) {
        Long threadId = Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId.toString(), timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        redisTemplate.delete(LOCK_PREFIX + name);
    }
}
