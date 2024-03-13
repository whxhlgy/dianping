package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.util.concurrent.Striped;

import java.time.LocalDateTime;
import java.util.concurrent.locks.Lock;

import javax.annotation.Resource;

import org.springframework.context.annotation.Lazy;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker idWorker;

    @Resource
    private IVoucherOrderService orderService;
    
    @Lazy
    @Resource
    VoucherOrderServiceImpl self;
    
    final Striped<Lock> userLocks = Striped.lazyWeakLock(100);

    @Override
    public Result createSeckillOrder(Long voucherId) {
        // query the id
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // check if the voucher in the valid time period
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(beginTime) || now.isAfter(endTime)) {
            return Result.fail("you are not in the valid time period to use the voucher!");
        }
        Long userId = UserHolder.getUser().getId();
        @SuppressWarnings("null")
        Lock lock = userLocks.get(userId);
        try {
            lock.lock();
            return self.createVoucherOrder(voucherId, voucher);
        } finally {
            lock.unlock();
        }
    }


    @Transactional
    public Result createVoucherOrder(Long voucherId, SeckillVoucher voucher) {
        Long userId = UserHolder.getUser().getId();
        // check if the user has the voucher
        long orderId = idWorker.nextID("order");
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("you have already got the voucher!");
        }

        // check if there are enough stock
        if (voucher.getStock() < 1) {
            return Result.fail("no enough stock for the voucher!");
        }
        boolean update_success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!update_success) {
            return Result.fail("no enough stock for the voucher!");
        }

        // create a new order
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        orderService.save(order);
        // 返回结果
        return Result.ok(orderId);
    }
}
