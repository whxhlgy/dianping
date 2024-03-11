package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisIdWorkerTest {

    @Resource
    RedisIdWorker redisIdWorker;

    ExecutorService executorService = Executors.newFixedThreadPool(100);

    @Test
    void nextID() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(500);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long orderId = redisIdWorker.nextID("order");
//                System.out.println(orderId);
            }
            countDownLatch.countDown();
        };

        long begin = System.currentTimeMillis();

        for (int i = 0; i < 500; i++) {
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("Time cost: " + (end - begin));
    }
}