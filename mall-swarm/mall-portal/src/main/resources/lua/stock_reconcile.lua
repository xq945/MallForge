-- 对账 CAS：只有当前值仍等于读取到的值时才覆盖，避免覆盖并发预扣。
-- KEYS[1] = 库存 key
-- ARGV[1] = 读取到的当前值
-- ARGV[2] = 期望写入值
-- 返回 1 表示已修复，0 表示期间有并发变更、放弃本轮。
local current = redis.call('GET', KEYS[1])
if current == ARGV[1] then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
end
return 0
