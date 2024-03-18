package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker idWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Lazy
    @Resource
    VoucherOrderServiceImpl self;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // XGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    String streamKey = "stream.orders";
                    @SuppressWarnings("unchecked")
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().block(Duration.ofSeconds(2)).count(1),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    MapRecord<String,Object,Object> record = list.get(0);
                    Map<Object,Object> valueMap = record.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(valueMap, new VoucherOrder(), true);
                    handleOrder(order);
                    // send ack
                    stringRedisTemplate.opsForStream().acknowledge(streamKey, "g1", record.getId());
                } catch (Exception e) {
                    log.error("error when processsing orders:", e);
                    handleError();
                }
            }
        }

        private void handleError() {
            while (true) {
                try {
                    // read messages from pending list
                    String streamKey = "stream.orders";
                    @SuppressWarnings("unchecked")
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(streamKey, ReadOffset.from("0")));
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    MapRecord<String,Object,Object> record = list.get(0);
                    Map<Object,Object> valueMap = record.getValue();
                    VoucherOrder order = BeanUtil.fillBeanWithMap(valueMap, new VoucherOrder(), true);
                    handleOrder(order);
                    // send ack
                    stringRedisTemplate.opsForStream().acknowledge(streamKey, "g1", record.getId());
                } catch (Exception e) {
                    log.error("error when processsing orders in pending-list:", e);
                }
            }
        }

        private void handleOrder(VoucherOrder order) {
            RLock lock = redissonClient.getLock("lock:order:" + order.getUserId());
            if (!lock.tryLock()) {
                log.error("no repeat orders");
                return;
            }
            try {
                self.saveOrder(order);
            } finally {
                lock.unlock();
            }
        }
    }

    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result createSeckillOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = idWorker.nextID("order");
        // check if the seckill order valid
        Long res = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                orderId.toString());
        if (res != 0) {
            return Result.fail(res == 1 ? "no enough stock" : "no repeat orders");
        }
        return Result.ok(orderId);
    }

    @Transactional
    public void saveOrder(VoucherOrder order) {
        Long userId = order.getUserId();
        long voucherId = order.getVoucherId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("you have already got the voucher!");
            return;
        }

        boolean update_success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!update_success) {
            log.error("no enough stock for the voucher!");
            return;
        }

        // create a new order
        save(order);
    }
}
