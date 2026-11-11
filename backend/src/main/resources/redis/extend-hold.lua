-- Gives a hold time to be paid: every key of the hold lives at least until now + window (never shortened).
-- KEYS: the hold. ARGV: hold id, customer, window ms.
-- Returns {show, seat ids csv, expiresAt epoch ms}, or {} if the hold is gone or someone else's.
-- Builds the other keys itself: fine on one Redis, Cluster would need hash tags.
local hold = redis.call('HMGET', KEYS[1], 'customer', 'show', 'seats', 'expiresAt')
if hold[1] ~= ARGV[2] then
    return {}
end

local holdId, customer, show, window = ARGV[1], ARGV[2], hold[2], tonumber(ARGV[3])
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local expiresAt = math.max(now + window, tonumber(hold[4]))

local heldKey = 'held:' .. show
for seat in string.gmatch(hold[3], '[^,]+') do
    local seatKey = 'seat:' .. show .. ':' .. seat
    if redis.call('GET', seatKey) == holdId then
        redis.call('PEXPIRE', seatKey, window, 'GT')
        redis.call('ZADD', heldKey, 'GT', expiresAt, seat)
    end
end
redis.call('PEXPIRE', heldKey, window, 'GT')

local userHoldKey = 'user-hold:' .. show .. ':' .. customer
if redis.call('GET', userHoldKey) == holdId then
    redis.call('PEXPIRE', userHoldKey, window, 'GT')
end

local expiresAtText = string.format('%.0f', expiresAt)
redis.call('HSET', KEYS[1], 'expiresAt', expiresAtText)
redis.call('PEXPIRE', KEYS[1], window, 'GT')
return {show, hold[3], expiresAtText}
