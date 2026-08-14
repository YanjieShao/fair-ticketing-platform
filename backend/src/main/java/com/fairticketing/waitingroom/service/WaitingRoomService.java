package com.fairticketing.waitingroom.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.waitingroom.domain.WaitingRoomStatus;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A queue in front of the checkout for one event.
 *
 * <p>The inventory strategies decide who wins a race; this decides how many
 * people are allowed to race at all. Everything the queue needs is per event,
 * so a sold-out arena show cannot starve a small gig running at the same time.
 *
 * <p>Buyers are keyed by user id rather than by an anonymous token, which means
 * joining requires a login. That is a deliberate trade: it costs the pre-sale
 * lobby, and it buys the guarantee that one person cannot hold twenty places in
 * line by opening twenty tabs.
 */
@Service
public class WaitingRoomService {

    private static final String PREFIX = "ticketing:wr:";

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;
    private final TicketingProperties properties;
    private final Clock clock;

    public WaitingRoomService(StringRedisTemplate redis, TicketingProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;

        @SuppressWarnings({"rawtypes", "unchecked"})
        DefaultRedisScript<List> waitingRoom = new DefaultRedisScript<>();
        waitingRoom.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/waiting_room.lua")));
        waitingRoom.setResultType(List.class);
        this.script = waitingRoom;
    }

    public boolean enabled() {
        return properties.waitingRoom().enabled();
    }

    /** Takes a place in line, or reports the pass this buyer already holds. */
    public Pass join(Long eventId, Long userId) {
        return poll(eventId, userId, true);
    }

    /**
     * Reports where a buyer stands. Polling is what moves the line: each call
     * refills the bucket and lets through whoever it can pay for, so the queue
     * drains at the configured rate without a background job of its own.
     */
    public Pass status(Long eventId, Long userId) {
        return poll(eventId, userId, false);
    }

    /**
     * The gate the checkout calls. Read-only on purpose: buying should not be
     * able to admit anybody, including the buyer making the request.
     */
    public void requireAdmission(Long eventId, Long userId) {
        if (!enabled() || admitted(eventId, userId)) {
            return;
        }
        throw new BusinessException(ErrorCode.WAITING_ROOM_TOKEN_REQUIRED,
                "Join the waiting room for this event before checking out");
    }

    public boolean admitted(Long eventId, Long userId) {
        Double expiresAt = redis.opsForZSet().score(admittedKey(eventId), member(userId));
        return expiresAt != null && expiresAt > clock.millis();
    }

    /** Gives up a place or a pass, so someone else moves up immediately. */
    public void leave(Long eventId, Long userId) {
        redis.opsForZSet().remove(queueKey(eventId), member(userId));
        redis.opsForZSet().remove(admittedKey(eventId), member(userId));
    }

    private Pass poll(Long eventId, Long userId, boolean join) {
        TicketingProperties.WaitingRoom config = properties.waitingRoom();
        long now = clock.millis();

        List<?> result = redis.execute(script,
                List.of(queueKey(eventId), bucketKey(eventId), admittedKey(eventId)),
                member(userId),
                String.valueOf(now),
                String.valueOf(config.admitRatePerSecond()),
                String.valueOf(config.burst()),
                String.valueOf(config.admissionTtl().toMillis()),
                String.valueOf(config.maxAdmissionsPerPoll()),
                join ? "1" : "0",
                String.valueOf(config.idleTtl().toMillis()));

        long status = number(result, 0);
        long position = number(result, 1);
        long queueLength = number(result, 2);
        long expiresAt = number(result, 3);

        return new Pass(
                WaitingRoomStatus.ofCode(status),
                position,
                queueLength,
                expiresAt > 0 ? Instant.ofEpochMilli(expiresAt) : null,
                estimateWait(position, config.admitRatePerSecond()));
    }

    /**
     * Rounded up, because telling someone who is third in line at one per
     * second that they have two seconds left is how you get an angry refresh.
     */
    static Duration estimateWait(long position, double admitRatePerSecond) {
        if (position <= 0 || admitRatePerSecond <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds((long) Math.ceil(position / admitRatePerSecond));
    }

    private static long number(List<?> result, int index) {
        if (result == null || result.size() <= index) {
            return 0;
        }
        return result.get(index) instanceof Number value ? value.longValue() : 0;
    }

    private static String member(Long userId) {
        return String.valueOf(userId);
    }

    private static String queueKey(Long eventId) {
        return PREFIX + eventId + ":queue";
    }

    private static String bucketKey(Long eventId) {
        return PREFIX + eventId + ":bucket";
    }

    private static String admittedKey(Long eventId) {
        return PREFIX + eventId + ":admitted";
    }

    /**
     * @param admissionExpiresAt null while still waiting
     * @param estimatedWait      how long the current rate says the remaining
     *                           positions will take, ignoring people who give up
     */
    public record Pass(WaitingRoomStatus status,
                       long position,
                       long queueLength,
                       Instant admissionExpiresAt,
                       Duration estimatedWait) {
    }
}
