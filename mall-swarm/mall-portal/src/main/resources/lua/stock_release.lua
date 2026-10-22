-- 释放预扣库存
-- KEYS[1] = 库存 key
-- ARGV[1] = 释放数量
-- key 不存在时返回 -1，由对账任务按数据库重建，避免凭空造出库存。
if redis.call('EXISTS', KEYS[1]) == 0 then
    return -1
end
return redis.call('INCRBY', KEYS[1], tonumber(ARGV[1]))
