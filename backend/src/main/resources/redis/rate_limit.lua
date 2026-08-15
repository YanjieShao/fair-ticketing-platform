-- Atomic sliding-window counter: increment and set TTL only on the first hit
-- so later requests cannot stretch the window.
--
-- KEYS[1] counter
-- ARGV[1] window in milliseconds
--
-- returns the count after this hit

local n = redis.call('INCR', KEYS[1])
if n == 1 then
    redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1]))
end
return n
