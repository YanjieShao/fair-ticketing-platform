package com.fairticketing.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ticketing")
public record TicketingProperties(
        Inventory inventory,
        Order order,
        Waitlist waitlist,
        WaitingRoom waitingRoom,
        Payment payment,
        Security security,
        Seed seed
) {

    /**
     * Three ways to keep a tier from overselling, kept side by side so the load
     * test can measure them against each other instead of arguing about them.
     */
    public enum InventoryStrategy {
        /** SELECT ... FOR UPDATE. Obviously correct, serialises every buyer on one row. */
        DB_PESSIMISTIC_LOCK,
        /** One conditional UPDATE whose WHERE clause is the oversell guard. */
        DB_CONDITIONAL_UPDATE,
        /** Atomic decrement in a Redis Lua script, reconciled against the database. */
        REDIS_LUA
    }

    public record Inventory(InventoryStrategy strategy) {
    }

    /**
     * @param paymentWindow how long an unpaid order keeps its inventory
     */
    public record Order(Duration paymentWindow, int maxTicketsPerUserPerTier) {
    }

    /**
     * @param offerWindow exclusive purchase window granted to the head of the queue
     */
    public record Waitlist(Duration offerWindow) {
    }

    /**
     * Throttles how fast buyers reach the checkout, one queue per event.
     *
     * @param enabled              off by default so load tests can measure the
     *                             inventory strategies without a gate in front
     * @param admitRatePerSecond   admissions per second, the sustained drain rate
     * @param burst                bucket capacity, how much of a quiet period is
     *                             carried over into a sudden arrival
     * @param admissionTtl         how long a pass is good for once granted
     * @param maxAdmissionsPerPoll caps the work a single poll can do, so one
     *                             request never walks a queue of 100k
     * @param idleTtl              when an untouched room is dropped from Redis
     */
    public record WaitingRoom(boolean enabled,
                              double admitRatePerSecond,
                              int burst,
                              Duration admissionTtl,
                              int maxAdmissionsPerPoll,
                              Duration idleTtl) {
    }

    /**
     * @param failureRate 0.0 to 1.0, used to demonstrate the paths that release inventory
     */
    public record Payment(double failureRate) {
    }

    public record Security(String jwtSecret, Duration accessTokenTtl) {
    }

    /**
     * A brand new platform has no sales history, so the forecasting model has
     * nothing to learn from. This generates that history.
     *
     * @param randomSeed fixed so every run produces the same dataset
     */
    public record Seed(boolean enabled,
                       int artists,
                       int venues,
                       int pastEvents,
                       int upcomingEvents,
                       int buyers,
                       long randomSeed) {
    }
}
