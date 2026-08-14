-- Holds tickets for one tier.
--
-- The read and the decrement have to be one indivisible step: checking
-- availability and then decrementing in two round trips is exactly the race
-- that oversells. Redis runs this script single-threaded, so no other buyer
-- can see the counter in between.
--
-- KEYS[1] remaining tickets for the tier
-- ARGV[1] how many are wanted
--
-- returns  1 held
--          0 not enough left
--         -1 counter missing, the caller has to load it from the database

local remaining = redis.call('GET', KEYS[1])
if remaining == false then
    return -1
end

local wanted = tonumber(ARGV[1])
if tonumber(remaining) < wanted then
    return 0
end

redis.call('DECRBY', KEYS[1], wanted)
return 1
