package com.fairticketing.common.ratelimit;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.common.ratelimit.AccountRateLimiter.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRateLimiterTest {

    private final FakeStore store = new FakeStore();
    private AccountRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AccountRateLimiter(store, properties(true, 2, 3, 10));
    }

    @Test
    void allows_requests_up_to_the_per_minute_cap() {
        limiter.guard(7L, Action.CHECKOUT);
        limiter.guard(7L, Action.CHECKOUT);
        assertThatThrownBy(() -> limiter.guard(7L, Action.CHECKOUT))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void counts_checkout_and_join_separately() {
        limiter.guard(7L, Action.CHECKOUT);
        limiter.guard(7L, Action.CHECKOUT);
        assertThatCode(() -> limiter.guard(7L, Action.JOIN)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.guard(7L, Action.JOIN)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.guard(7L, Action.JOIN)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.guard(7L, Action.JOIN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void does_not_share_counters_across_accounts() {
        limiter.guard(7L, Action.CHECKOUT);
        limiter.guard(7L, Action.CHECKOUT);
        assertThatCode(() -> limiter.guard(8L, Action.CHECKOUT)).doesNotThrowAnyException();
    }

    @Test
    void treats_a_short_window_spike_as_an_anomaly() {
        limiter = new AccountRateLimiter(store, properties(true, 20, 20, 2));
        limiter.guard(7L, Action.CHECKOUT);
        limiter.guard(7L, Action.JOIN);
        assertThatThrownBy(() -> limiter.guard(7L, Action.CHECKOUT))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(store.counts.get("rl:burst:7").get()).isEqualTo(3);
    }

    @Test
    void does_nothing_when_disabled() {
        limiter = new AccountRateLimiter(store, properties(false, 1, 1, 1));
        limiter.guard(7L, Action.CHECKOUT);
        limiter.guard(7L, Action.CHECKOUT);
        assertThat(store.counts).isEmpty();
    }

    @Test
    void interceptor_maps_only_the_hot_posts() {
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/orders")).isEqualTo(Action.CHECKOUT);
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/waitlist")).isEqualTo(Action.JOIN);
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/waiting-room/12/join")).isEqualTo(Action.JOIN);
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/orders/FT1/pay")).isNull();
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/waiting-room/12")).isNull();
        assertThat(HotPathRateLimitInterceptor.actionFor("/api/events")).isNull();
    }

    private static TicketingProperties properties(boolean enabled,
                                                  int checkoutPerMinute,
                                                  int joinPerMinute,
                                                  int burst) {
        return new TicketingProperties(
                new TicketingProperties.Inventory(InventoryStrategy.DB_PESSIMISTIC_LOCK),
                new TicketingProperties.Order(Duration.ofMinutes(10), 4),
                new TicketingProperties.Waitlist(Duration.ofMinutes(15)),
                new TicketingProperties.WaitingRoom(false, 20, 50, Duration.ofMinutes(5), 200, Duration.ofHours(12)),
                new TicketingProperties.Payment(0.0),
                new TicketingProperties.Security("test-secret-that-is-long-enough-32", Duration.ofHours(2)),
                new TicketingProperties.Seed(false, 0, 0, 0, 0, 0, 1L),
                new TicketingProperties.Cors(List.of("http://localhost:5173")),
                new TicketingProperties.Ml("http://127.0.0.1:9", Duration.ofSeconds(1), false),
                new TicketingProperties.Llm("", "http://127.0.0.1:9", "gpt-4o-mini", Duration.ofSeconds(1), false),
                new TicketingProperties.LoadTest(false),
                new TicketingProperties.RateLimit(enabled, checkoutPerMinute, joinPerMinute, burst));
    }

    private static final class FakeStore implements RateLimitStore {
        private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

        @Override
        public long increment(String key, Duration window) {
            return counts.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
        }
    }
}
