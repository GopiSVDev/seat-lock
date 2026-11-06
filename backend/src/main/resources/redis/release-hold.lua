-- Deletes a hold and frees its seats. KEYS: the hold. ARGV: hold id, customer.
-- Returns 1, or 0 if the hold is gone (expired, confirmed, released) or someone else's.
local hold = redis.call('HMGET', KEYS[1], 'customer', 'show', 'seats')
if hold[1] ~= ARGV[2] then
    return 0
end

local holdId, show = ARGV[1], hold[2]
for seat in string.gmatch(hold[3], '[^,]+') do
    local seatKey = 'seat:' .. show .. ':' .. seat
    if redis.call('GET', seatKey) == holdId then
        redis.call('DEL', seatKey)
        redis.call('ZREM', 'held:' .. show, seat)
    end
end

local userHoldKey = 'user-hold:' .. show .. ':' .. ARGV[2]
if redis.call('GET', userHoldKey) == holdId then
    redis.call('DEL', userHoldKey)
end
redis.call('DEL', KEYS[1])
return 1
