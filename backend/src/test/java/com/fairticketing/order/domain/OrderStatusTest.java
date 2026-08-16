package com.fairticketing.order.domain;

import com.fairticketing.common.domain.IllegalStateTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        void walks_from_creation_to_completion() {
            OrderStatus status = OrderStatus.CREATED
                    .transitionTo(OrderStatus.PENDING_PAYMENT)
                    .transitionTo(OrderStatus.PAID)
                    .transitionTo(OrderStatus.COMPLETED);

            assertThat(status).isEqualTo(OrderStatus.COMPLETED);
            assertThat(status.isTerminal()).isFalse();
            assertThat(status.transitionTo(OrderStatus.CANCELLED)).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("inventory release")
    class InventoryRelease {

        @Test
        void unpaid_order_can_expire_or_be_cancelled() {
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
            assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
            assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        void paid_or_completed_order_can_be_returned() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        void paid_order_cannot_silently_expire() {
            assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.EXPIRED)).isFalse();
            assertThat(OrderStatus.PAID.canExpire()).isFalse();
            assertThat(OrderStatus.COMPLETED.canExpire()).isFalse();
            assertThat(OrderStatus.PENDING_PAYMENT.canExpire()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class,
                names = {"CREATED", "PENDING_PAYMENT", "PAID", "COMPLETED"})
        void active_states_hold_inventory(OrderStatus status) {
            assertThat(status.occupiesInventory()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"EXPIRED", "CANCELLED"})
        void closed_states_release_inventory(OrderStatus status) {
            assertThat(status.occupiesInventory()).isFalse();
        }
    }

    @Nested
    @DisplayName("terminal states")
    class Terminal {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"EXPIRED", "CANCELLED"})
        void reject_every_further_transition(OrderStatus terminal) {
            assertThat(terminal.isTerminal()).isTrue();
            for (OrderStatus target : OrderStatus.values()) {
                assertThatThrownBy(() -> terminal.transitionTo(target))
                        .isInstanceOf(IllegalStateTransitionException.class);
            }
        }
    }

    @Test
    @DisplayName("no state may transition to itself")
    void self_transitions_are_rejected() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }

    @Test
    @DisplayName("the transition table matches the documented state machine exactly")
    void transition_table_is_locked_down() {
        assertThat(OrderStatus.CREATED.allowedTargets())
                .isEqualTo(EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, OrderStatus.EXPIRED));
        assertThat(OrderStatus.PENDING_PAYMENT.allowedTargets())
                .isEqualTo(EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.EXPIRED));
        assertThat(OrderStatus.PAID.allowedTargets())
                .isEqualTo(EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        assertThat(OrderStatus.COMPLETED.allowedTargets())
                .isEqualTo(EnumSet.of(OrderStatus.CANCELLED));
        assertThat(OrderStatus.EXPIRED.allowedTargets()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedTargets()).isEmpty();
    }
}
