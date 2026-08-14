package com.fairticketing.waitlist.domain;

import com.fairticketing.common.domain.IllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitlistStatusTest {

    @Test
    void offer_can_be_taken_up() {
        WaitlistStatus status = WaitlistStatus.WAITING
                .transitionTo(WaitlistStatus.OFFERED)
                .transitionTo(WaitlistStatus.CONVERTED);

        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("an unused offer expires so the next entry can be served")
    void offer_can_expire() {
        assertThat(WaitlistStatus.OFFERED.canTransitionTo(WaitlistStatus.OFFER_EXPIRED)).isTrue();
    }

    @Test
    @DisplayName("an entry cannot convert without first receiving an offer")
    void waiting_cannot_convert_directly() {
        assertThatThrownBy(() -> WaitlistStatus.WAITING.transitionTo(WaitlistStatus.CONVERTED))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @DisplayName("an expired offer does not silently return to the queue")
    void expired_offer_is_terminal() {
        assertThat(WaitlistStatus.OFFER_EXPIRED.canTransitionTo(WaitlistStatus.WAITING)).isFalse();
        assertThat(WaitlistStatus.OFFER_EXPIRED.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = WaitlistStatus.class, names = {"WAITING", "OFFERED"})
    void active_entries_compete_for_released_inventory(WaitlistStatus status) {
        assertThat(status.isActive()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = WaitlistStatus.class, names = {"CONVERTED", "OFFER_EXPIRED", "CANCELLED"})
    void finished_entries_are_out_of_the_queue(WaitlistStatus status) {
        assertThat(status.isActive()).isFalse();
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    void transition_table_is_locked_down() {
        assertThat(WaitlistStatus.WAITING.allowedTargets())
                .isEqualTo(EnumSet.of(WaitlistStatus.OFFERED, WaitlistStatus.CANCELLED));
        assertThat(WaitlistStatus.OFFERED.allowedTargets())
                .isEqualTo(EnumSet.of(WaitlistStatus.CONVERTED, WaitlistStatus.OFFER_EXPIRED, WaitlistStatus.CANCELLED));
    }
}
