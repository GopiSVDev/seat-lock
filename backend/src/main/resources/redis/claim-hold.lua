-- Claims a hold for confirmation: keeps its keys alive for at least the grace period,
-- so the seats can't expire and be grabbed while the booking is written.
-- KEYS: the hold. ARGV: hold id, customer, grace ms.
-- Returns {show, seat ids csv}, or {} if the hold is gone or someone else's.
local hold = redis.call('HMGET', KEYS[1], 'customer', 'show', 'seats')
if hold[1] ~= ARGV[2] then
    return {}
end

local holdId, show, grace = ARGV[1], hold[2], ARGV[3]
for seat in string.gmatch(hold[3], '[^,]+') do
    local seatKey = 'seat:' .. show .. ':' .. seat
    if redis.call('GET', seatKey) == holdId then
        redis.call('PEXPIRE', seatKey, grace, 'GT')
    end
end
redis.call('PEXPIRE', KEYS[1], grace, 'GT')
return {show, hold[3]}
