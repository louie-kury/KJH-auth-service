if redis.call('GET', KEYS[1]) ~= ARGV[1] then
    return 0
end

if ARGV[2] ~= '' then
    redis.call('HSET', KEYS[2], 'digest', ARGV[2], 'failures', 0)
    redis.call('PEXPIRE', KEYS[2], 300000)
end
redis.call('DEL', KEYS[1])
return 1
