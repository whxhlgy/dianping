package com.hmdp.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import com.hmdp.service.ILock;

public class SimpleRedisLock implements ILock {

    private static final String LOCK_PREFIX = "lock::";
    private static final String ID_PREFIX = UUID.randomUUID() + "-";
    private final String name;
    private final String threadId;
    private final StringRedisTemplate redisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<Long>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.name = name;
        this.threadId = ID_PREFIX + Thread.currentThread().getId();
    }

    @Override
    public boolean tryLock(long timeOutSec) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + name, threadId, timeOutSec,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        redisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + name),
                threadId);
    }
}
