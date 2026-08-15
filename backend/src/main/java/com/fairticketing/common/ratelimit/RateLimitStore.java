package com.fairticketing.common.ratelimit;

import java.time.Duration;

/**
 * Increments a named counter that expires after {@code window}. Implementations
 * must be safe for concurrent callers of the same key.
 */
public interface RateLimitStore {

    long increment(String key, Duration window);
}
