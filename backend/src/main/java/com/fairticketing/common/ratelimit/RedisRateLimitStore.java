package com.fairticketing.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis is already on the box for the waiting room. If it is down, the limiter
 * fails open so a cache blip does not take checkout with it when the inventory
 * strategy is a database lock.
 */
@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
        DefaultRedisScript<Long> loaded = new DefaultRedisScript<>();
        loaded.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/rate_limit.lua")));
        loaded.setResultType(Long.class);
        this.script = loaded;
    }

    @Override
    public long increment(String key, Duration window) {
        try {
            Long count = redis.execute(script, List.of(key), String.valueOf(window.toMillis()));
            return count == null ? 0L : count;
        } catch (RuntimeException failed) {
            log.warn("Rate-limit store unavailable for {}; allowing the request", key, failed);
            return 0L;
        }
    }
}
