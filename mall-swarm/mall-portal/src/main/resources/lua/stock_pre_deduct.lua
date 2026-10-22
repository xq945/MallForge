-- 原子预扣可售库存
-- KEYS[1] = 库存 key
-- ARGV[1] = key 不存在时用于初始化的可售库存（-1 表示未知，拒绝初始化）
-- ARGV[2] = 扣减数量
-- 返回：剩余可售库存；-1 库存不足；-2 参数非法；-3 无法初始化
local current = redis.call('GET', KEYS[1])
if not current then
    local init = tonumber(ARGV[1])
    if init == nil or init < 0 then
        return -3
    end
    redis.call('SET', KEYS[1], init)
    current = init
end
local avail = tonumber(current)
local qty = tonumber(ARGV[2])
if avail == nil or qty == nil or qty <= 0 then
    return -2
end
if avail < qty then
    return -1
end
redis.call('DECRBY', KEYS[1], qty)
return avail - qty
