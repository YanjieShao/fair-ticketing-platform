package com.fairticketing.waitingroom.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.support.Fixtures;
import com.fairticketing.waitingroom.domain.WaitingRoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitingRoomServiceTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private WaitingRoomService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        TicketingProperties properties = new TicketingProperties(
                Fixtures.properties().inventory(),
                Fixtures.properties().order(),
                Fixtures.properties().waitlist(),
                new TicketingProperties.WaitingRoom(true, 20, 50, Duration.ofMinutes(5), 200, Duration.ofHours(12)),
                Fixtures.properties().payment(),
                Fixtures.properties().security(),
                Fixtures.properties().seed(),
                Fixtures.properties().cors(),
                Fixtures.properties().ml(),
                Fixtures.properties().llm(),
                Fixtures.properties().loadTest(),
                Fixtures.properties().rateLimit());
        service = new WaitingRoomService(redis, properties, java.time.Clock.fixed(Fixtures.NOW, java.time.ZoneOffset.UTC));
    }

    @Test
    void an_admitted_buyer_is_not_told_to_wait() {
        assertThat(WaitingRoomService.estimateWait(0, 20)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("wait is rounded up, so third in line at one per second is not told two seconds")
    void rounds_up_to_the_next_second() {
        assertThat(WaitingRoomService.estimateWait(3, 1.0)).isEqualTo(Duration.ofSeconds(3));
        assertThat(WaitingRoomService.estimateWait(1, 20)).isEqualTo(Duration.ofSeconds(1));
        assertThat(WaitingRoomService.estimateWait(21, 20)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void a_stopped_room_does_not_promise_a_wait() {
        assertThat(WaitingRoomService.estimateWait(40, 0)).isEqualTo(Duration.ZERO);
    }

    @Test
    void join_reads_the_lua_result_as_a_pass() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(1, 4, 10, 0));
        WaitingRoomService.Pass pass = service.join(5L, 7L);
        assertThat(pass.status()).isEqualTo(WaitingRoomStatus.WAITING);
        assertThat(pass.position()).isEqualTo(4);
        assertThat(pass.admissionExpiresAt()).isNull();
    }

    @Test
    void status_keeps_an_admission_expiry() {
        long expires = Fixtures.NOW.plusSeconds(60).toEpochMilli();
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(2, 0, 9, expires));
        WaitingRoomService.Pass pass = service.status(5L, 7L);
        assertThat(pass.status()).isEqualTo(WaitingRoomStatus.ADMITTED);
        assertThat(pass.admissionExpiresAt()).isEqualTo(Instant.ofEpochMilli(expires));
    }

    @Test
    void a_short_or_null_lua_result_is_treated_as_not_queued() {
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);
        assertThat(service.join(5L, 7L).status()).isEqualTo(WaitingRoomStatus.NOT_QUEUED);
        when(redis.execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("x"));
        assertThat(service.status(5L, 7L).status()).isEqualTo(WaitingRoomStatus.NOT_QUEUED);
    }

    @Test
    void requireAdmission_lets_through_an_admitted_buyer_and_blocks_the_rest() {
        when(zset.score(anyString(), anyString())).thenReturn((double) Fixtures.NOW.plusSeconds(30).toEpochMilli());
        service.requireAdmission(5L, 7L, true);

        when(zset.score(anyString(), anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.requireAdmission(5L, 7L, true))
                .isInstanceOf(BusinessException.class);
        service.requireAdmission(5L, 7L, false);
    }

    @Test
    void leave_drops_both_the_place_in_line_and_the_pass() {
        service.leave(5L, 7L);
        verify(zset).remove("ticketing:wr:5:queue", "7");
        verify(zset).remove("ticketing:wr:5:admitted", "7");
    }

    @Test
    void enabled_follows_config() {
        assertThat(service.enabled()).isTrue();
    }
}
