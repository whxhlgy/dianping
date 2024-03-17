-- check if the voucher order is valid
-- arguments
-- 1. id of the voucher
-- 2. id of user


local voucherId = ARGV[1]
local userId = ARGV[2]
local voucherKey = 'seckill:stock:' .. voucherId
local orderSetKey = 'seckill:order:' .. userId

local stock = tonumber(redis.call('GET', voucherKey) or 0)

-- the voucher is out of stock
if stock <= 0 then
    return 1
end

-- the voucher has been ordered by the user
if redis.call('SISMEMBER', orderSetKey, voucherId) == 1 then
    return 2
end

-- decrease the stock and add the order
redis.call('DECR', voucherKey)
-- add the user in orderSet
redis.call('SADD', orderSetKey, voucherId)
-- 
-- 
return 0