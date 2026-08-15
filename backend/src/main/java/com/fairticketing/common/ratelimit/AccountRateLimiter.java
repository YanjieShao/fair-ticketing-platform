package com.fairticketing.common.ratelimit;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.config.TicketingProperties.RateLimit;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Caps how often one account can hit checkout or join. The burst window is the
 * anomaly detector: a script hammering from a single user trips it long before
 * a person retrying a slow page would.
 *
 * <p>Keyed by user, not IP, so the 10k laptop stampede (one request per buyer,
 * one IP) is not mistaken for a bot.
 */
@Component
public class AccountRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AccountRateLimiter.class);
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

    public enum Action {
        CHECKOUT,
        JOIN
    }

    private final RateLimitStore store;
    private final TicketingProperties properties;

    public AccountRateLimiter(RateLimitStore store, TicketingProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public void guard(long userId, Action action) {
        RateLimit limits = properties.rateLimit();
        if (!limits.enabled()) {
            return;
        }

        long burst = store.increment("rl:burst:" + userId, TEN_SECONDS);
        if (burst > limits.burstPerTenSeconds()) {
            log.warn("Anomalous request rate user={} count={} window=10s", userId, burst);
            reject();
        }

        String key = action == Action.CHECKOUT ? "rl:checkout:" + userId : "rl:join:" + userId;
        int cap = action == Action.CHECKOUT ? limits.checkoutPerMinute() : limits.joinPerMinute();
        if (store.increment(key, MINUTE) > cap) {
            reject();
        }
    }

    private static void reject() {
        throw new BusinessException(ErrorCode.RATE_LIMITED,
                "Too many requests from this account; wait a moment and retry");
    }
}
