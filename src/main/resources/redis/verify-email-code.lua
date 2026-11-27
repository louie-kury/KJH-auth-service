local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local function waitForCapacity(key, window, limit)
    redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
    if redis.call('ZCARD', key) < limit then
        return 0
    end
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    return tonumber(oldest[2]) + window - now
end

local function count(key, window)
    redis.call('ZADD', key, now, ARGV[1])
    redis.call('PEXPIRE', key, window)
end

local retry = waitForCapacity(KEYS[2], 3600000, 10)
if retry > 0 then
    return math.ceil(retry / 1000)
end

local digest = redis.call('HGET', KEYS[1], 'digest')
if digest == ARGV[2] then
    redis.call('DEL', KEYS[1])
    return 0
end

count(KEYS[2], 3600000)
if not digest then
    return -2
end
if redis.call('HINCRBY', KEYS[1], 'failures', 1) >= 5 then
    redis.call('DEL', KEYS[1])
end
return -1
