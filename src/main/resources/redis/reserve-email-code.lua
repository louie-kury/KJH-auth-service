local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local function waitForCapacity(key, window, limit)
    redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
    local size = redis.call('ZCARD', key)
    if size < limit then
        return 0
    end
    local oldest = redis.call('ZRANGE', key, size - limit, size - limit, 'WITHSCORES')
    return tonumber(oldest[2]) + window - now
end

local function count(key, window)
    redis.call('ZADD', key, now, ARGV[1])
    redis.call('PEXPIRE', key, window)
end

local cooldownWait = math.max(redis.call('PTTL', KEYS[1]), redis.call('PTTL', KEYS[2]))
local emailWait = waitForCapacity(KEYS[3], 3600000, 5)
local ipWait = waitForCapacity(KEYS[4], 3600000, 30)
local globalWait = waitForCapacity(KEYS[5], 60000, tonumber(ARGV[2]))
local retry = math.max(cooldownWait, emailWait, ipWait, globalWait)
if retry > 0 then
    local reason = 'RATE_LIMITED'
    if emailWait > 0 then
        reason = 'EMAIL_SEND_LIMIT_EXCEEDED'
    elseif cooldownWait > 0 then
        reason = 'EMAIL_RESEND_COOLDOWN'
    end
    return {math.ceil(retry / 1000), reason}
end
if redis.call('HGET', KEYS[6], 'digest') == ARGV[3] then
    return {-1, ''}
end

redis.call('PSETEX', KEYS[1], 60000, ARGV[1])
redis.call('PSETEX', KEYS[2], 60000, '1')
count(KEYS[3], 3600000)
count(KEYS[4], 3600000)
count(KEYS[5], 60000)
return {0, ''}
