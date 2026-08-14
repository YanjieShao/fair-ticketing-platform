package com.fairticketing.event.domain;

import com.fairticketing.common.domain.IllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStatusTest {

    @Test
    @DisplayName("a sold out event goes back on sale when inventory is returned")
    void sold_out_is_reversible() {
        assertThat(EventStatus.SOLD_OUT.transitionTo(EventStatus.ON_SALE))
                .isEqualTo(EventStatus.ON_SALE);
    }

    @Test
    @DisplayName("only events that have not gone on sale can be cancelled")
    void cancellation_is_limited_to_drafts() {
        assertThat(EventStatus.DRAFT.canTransitionTo(EventStatus.CANCELLED)).isTrue();
        assertThatThrownBy(() -> EventStatus.ON_SALE.transitionTo(EventStatus.CANCELLED))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> EventStatus.SOLD_OUT.transitionTo(EventStatus.CANCELLED))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void purchases_are_only_accepted_while_on_sale() {
        assertThat(EventStatus.ON_SALE.acceptsPurchases()).isTrue();
        assertThat(EventStatus.DRAFT.acceptsPurchases()).isFalse();
        assertThat(EventStatus.SOLD_OUT.acceptsPurchases()).isFalse();
        assertThat(EventStatus.CLOSED.acceptsPurchases()).isFalse();
        assertThat(EventStatus.CANCELLED.acceptsPurchases()).isFalse();
    }

    @Test
    void closed_and_cancelled_are_terminal() {
        assertThat(EventStatus.CLOSED.isTerminal()).isTrue();
        assertThat(EventStatus.CANCELLED.isTerminal()).isTrue();
    }
}
