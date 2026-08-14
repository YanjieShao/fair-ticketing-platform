-- Queue a buyer for one event and let the head of the line through at a fixed rate.
--
-- Joining, refilling the bucket, admitting and reporting position all happen in
-- one script because they read each other's results: two clients polling at the
-- same millisecond must not both spend the same token, and a buyer must never
-- see a position that a concurrent admission has already invalidated.
--
-- KEYS[1] queue      ZSET member -> join time, so ZRANK is the position
-- KEYS[2] bucket     HASH tokens + refilled_at, the token bucket state
-- KEYS[3] admitted   ZSET member -> expiry, so expiry is a range delete
--
-- ARGV[1] member         the buyer
-- ARGV[2] now            epoch millis, passed in so the script stays deterministic
-- ARGV[3] rate           admissions per second
-- ARGV[4] burst          bucket capacity, the size of an idle-period catch-up
-- ARGV[5] admissionTtl   millis a pass stays valid once granted
-- ARGV[6] maxDrain       admissions per call, caps the work one poll can do
-- ARGV[7] join           1 to enqueue the member, 0 to only look
-- ARGV[8] keyTtl         millis of idleness after which the whole room is dropped
--
-- Returns {status, position, queueLength, admissionExpiresAt}
-- where status is 0 = not queued, 1 = waiting, 2 = admitted.

local queue, bucket, admitted = KEYS[1], KEYS[2], KEYS[3]
local member = ARGV[1]
local now = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])
local burst = tonumber(ARGV[4])
local admissionTtl = tonumber(ARGV[5])
local maxDrain = tonumber(ARGV[6])
local join = tonumber(ARGV[7])
local keyTtl = tonumber(ARGV[8])

redis.call('ZREMRANGEBYSCORE', admitted, '-inf', now)

-- Re-queueing someone who already holds a pass would send them to the back of
-- the line for the rest of their session.
if join == 1 and redis.call('ZSCORE', admitted, member) == false then
  redis.call('ZADD', queue, 'NX', now, member)
end

local tokens = tonumber(redis.call('HGET', bucket, 'tokens'))
local refilledAt = tonumber(redis.call('HGET', bucket, 'refilled_at'))
if tokens == nil or refilledAt == nil then
  -- A room nobody has polled yet starts full, so the first arrivals of a
  -- quiet sale are not made to wait for tokens that were never spent.
  tokens = burst
  refilledAt = now
elseif now > refilledAt then
  tokens = math.min(burst, tokens + (now - refilledAt) * rate / 1000)
  refilledAt = now
end

local drain = math.floor(tokens)
if drain > maxDrain then drain = maxDrain end
local queued = redis.call('ZCARD', queue)
if drain > queued then drain = queued end

if drain > 0 then
  local heads = redis.call('ZPOPMIN', queue, drain)
  -- ZPOPMIN returns member, score, member, score, ...
  for i = 1, #heads, 2 do
    redis.call('ZADD', admitted, now + admissionTtl, heads[i])
  end
  tokens = tokens - drain
end

redis.call('HSET', bucket, 'tokens', tokens, 'refilled_at', refilledAt)
redis.call('PEXPIRE', bucket, keyTtl)
redis.call('PEXPIRE', queue, keyTtl)
redis.call('PEXPIRE', admitted, keyTtl)

local expiresAt = redis.call('ZSCORE', admitted, member)
if expiresAt ~= false then
  return {2, 0, redis.call('ZCARD', queue), math.floor(tonumber(expiresAt))}
end

local rank = redis.call('ZRANK', queue, member)
if rank == false then
  return {0, 0, redis.call('ZCARD', queue), 0}
end
return {1, rank + 1, redis.call('ZCARD', queue), 0}
