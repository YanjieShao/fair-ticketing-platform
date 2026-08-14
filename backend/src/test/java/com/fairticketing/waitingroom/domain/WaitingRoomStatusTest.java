package com.fairticketing.waitingroom.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaitingRoomStatusTest {

    @Test
    void lua_codes_map_onto_the_public_statuses() {
        assertThat(WaitingRoomStatus.ofCode(0)).isEqualTo(WaitingRoomStatus.NOT_QUEUED);
        assertThat(WaitingRoomStatus.ofCode(1)).isEqualTo(WaitingRoomStatus.WAITING);
        assertThat(WaitingRoomStatus.ofCode(2)).isEqualTo(WaitingRoomStatus.ADMITTED);
        assertThat(WaitingRoomStatus.ofCode(99)).isEqualTo(WaitingRoomStatus.NOT_QUEUED);
    }
}
