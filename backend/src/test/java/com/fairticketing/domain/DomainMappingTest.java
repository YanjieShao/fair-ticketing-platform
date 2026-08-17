package com.fairticketing.domain;

import com.fairticketing.ai.domain.DemandRiskLevel;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.order.domain.TicketOrder;
import com.fairticketing.order.web.OrderResponse;
import com.fairticketing.payment.domain.Payment;
import com.fairticketing.payment.service.PaymentGateway;
import com.fairticketing.support.Fixtures;
import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.web.WaitlistResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DomainMappingTest {

    @Test
    void ticket_tier_available_quantity_and_sold_out() {
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        assertThat(tier.availableQuantity()).isEqualTo(90);
        assertThat(tier.isSoldOut()).isFalse();
        tier.setReservedQuantity(100);
        assertThat(tier.isSoldOut()).isTrue();
    }

    @Test
    void order_transition_clears_the_lock_when_inventory_is_released() {
        TicketOrder order = TicketOrder.create("FT1", 7L, 5L, 42L, 2, 5000, "key",
                Fixtures.NOW, Fixtures.NOW.plusSeconds(60));
        assertThat(order.isExpiredAt(Fixtures.NOW.plusSeconds(120))).isTrue();
        order.transitionTo(OrderStatus.PENDING_PAYMENT, Fixtures.NOW);
        order.transitionTo(OrderStatus.PAID, Fixtures.NOW);
        assertThat(order.getPaidAt()).isEqualTo(Fixtures.NOW);
        order.transitionTo(OrderStatus.COMPLETED, Fixtures.NOW);
        assertThat(order.getCompletedAt()).isEqualTo(Fixtures.NOW);
        assertThat(order.getActiveLockKey()).isNotNull();
        order.transitionTo(OrderStatus.CANCELLED, Fixtures.NOW.plusSeconds(1));
        assertThat(order.getActiveLockKey()).isNull();
        assertThat(order.getClosedAt()).isEqualTo(Fixtures.NOW.plusSeconds(1));
    }

    @Test
    void waitlist_offer_expiry_and_mapping_default_timezone_to_utc() {
        WaitlistEntry entry = WaitlistEntry.join(5L, 42L, 7L, 2, 1, Fixtures.NOW);
        entry.offer(Fixtures.NOW, Fixtures.NOW.plus(Duration.ofMinutes(15)));
        assertThat(entry.isOfferExpiredAt(Fixtures.NOW.plus(Duration.ofMinutes(16)))).isTrue();
        entry.expireOffer();
        var response = WaitlistResponse.from(entry, 0, Fixtures.showViewWithoutTimezone(42L));
        assertThat(response.venueTimezone()).isEqualTo("UTC");
    }

    @Test
    void order_response_defaults_blank_timezone_to_utc_and_null_show_stays_blank() {
        TicketOrder order = TicketOrder.create("FT1", 7L, 5L, 42L, 1, 1000, "k", Fixtures.NOW, Fixtures.NOW);
        assertThat(OrderResponse.from(order, null).venueTimezone()).isEqualTo("UTC");
        assertThat(OrderResponse.from(order, Fixtures.showViewWithoutTimezone(42L)).venueTimezone()).isEqualTo("UTC");
    }

    @Test
    void payment_and_ledger_constructors_keep_the_fields() {
        Payment payment = new Payment(1L, "ref", Payment.Status.SUCCEEDED, 1000, Fixtures.NOW);
        assertThat(payment.getAmountCents()).isEqualTo(1000);
        assertThat(new PaymentGateway.Charge("ref", Payment.Status.SUCCEEDED).succeeded()).isTrue();
        InventoryLedgerEntry entry = new InventoryLedgerEntry(42L, 1L, 2, InventoryLedgerEntry.Reason.RESERVE, Fixtures.NOW);
        assertThat(entry.getDelta()).isEqualTo(2);
    }

    @Test
    void demand_risk_opens_the_waiting_room_only_when_high() {
        assertThat(DemandRiskLevel.fromRatio(1.2)).isEqualTo(DemandRiskLevel.HIGH);
        assertThat(DemandRiskLevel.fromRatio(0.8)).isEqualTo(DemandRiskLevel.MEDIUM);
        assertThat(DemandRiskLevel.fromRatio(0.2)).isEqualTo(DemandRiskLevel.LOW);
        assertThat(DemandRiskLevel.HIGH.shouldOpenWaitingRoom()).isTrue();
        assertThat(DemandRiskLevel.MEDIUM.shouldOpenWaitingRoom()).isFalse();
    }

    @Test
    void event_publish_and_unsold_cancel_are_covered_on_the_admin_service() {
        assertThat(EventStatus.DRAFT.canTransitionTo(EventStatus.ON_SALE)).isTrue();
    }
}
