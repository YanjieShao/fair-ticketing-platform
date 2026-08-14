package com.fairticketing.waitingroom.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingRoomServiceTest {

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
}
