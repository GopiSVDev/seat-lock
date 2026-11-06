-- Holds every requested seat or none.
-- KEYS: held set, the customer's hold pointer, the new hold, then one key per seat.
-- ARGV: hold id, ttl ms, customer, show, seat ids csv, then the seat ids in KEYS order.
-- Returns the expiry (epoch ms), or 0 if a seat is held by someone else.
-- Seats of the customer's previous hold for this show count as free; that hold is replaced.
-- Builds the previous hold's keys itself: fine on one Redis, Cluster would need hash tags.
local heldKey, userHoldKey, holdKey = KEYS[1], KEYS[2], KEYS[3]
local holdId, ttl, customer, show = ARGV[1], tonumber(ARGV[2]), ARGV[3], ARGV[4]
local previous = redis.call('GET', userHoldKey)

for i = 4, #KEYS do
    local owner = redis.call('GET', KEYS[i])
    if owner and owner ~= previous then
        return 0
    end
end

if previous then
    local seats = redis.call('HGET', 'hold:' .. previous, 'seats')
    if seats then
        for seat in string.gmatch(seats, '[^,]+') do
            local seatKey = 'seat:' .. show .. ':' .. seat
            if redis.call('GET', seatKey) == previous then
                redis.call('DEL', seatKey)
                redis.call('ZREM', heldKey, seat)
            end
        end
    end
    redis.call('DEL', 'hold:' .. previous)
end

-- Redis' clock, so every backend instance agrees on expiry
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local expiresAt = now + ttl

for i = 4, #KEYS do
    redis.call('SET', KEYS[i], holdId, 'PX', ttl)
    redis.call('ZADD', heldKey, expiresAt, ARGV[i + 2])
end
redis.call('ZREMRANGEBYSCORE', heldKey, '-inf', now)
redis.call('PEXPIRE', heldKey, ttl)
redis.call('HSET', holdKey, 'customer', customer, 'show', show, 'seats', ARGV[5],
        'expiresAt', string.format('%.0f', expiresAt))
redis.call('PEXPIRE', holdKey, ttl)
redis.call('SET', userHoldKey, holdId, 'PX', ttl)
return expiresAt
