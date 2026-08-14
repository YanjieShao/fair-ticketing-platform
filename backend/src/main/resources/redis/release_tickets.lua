-- Returns tickets to a tier, refusing to hand back more than were ever sold.
--
-- KEYS[1] remaining tickets for the tier
-- ARGV[1] how many are being returned
-- ARGV[2] the tier total, which the counter must never exceed
--
-- returns  1 returned
--         -1 counter missing, the caller has to load it from the database

local remaining = redis.call('GET', KEYS[1])
if remaining == false then
    return -1
end

local returned = tonumber(remaining) + tonumber(ARGV[1])
local total = tonumber(ARGV[2])
if returned > total then
    returned = total
end

redis.call('SET', KEYS[1], returned)
return 1
