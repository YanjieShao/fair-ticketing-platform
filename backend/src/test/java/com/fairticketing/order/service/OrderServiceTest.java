package com.fairticketing.order.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.repository.TierPurchaseView;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.order.domain.TicketOrder;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.payment.domain.Payment;
import com.fairticketing.payment.repository.PaymentRepository;
import com.fairticketing.payment.service.PaymentGateway;
import com.fairticketing.waitingroom.service.WaitingRoomService;
import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.service.WaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final Long USER_ID = 7L;
    private static final Long TIER_ID = 42L;
    private static final Long EVENT_ID = 5L;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final TicketOrderRepository orders = mock(TicketOrderRepository.class);
    private final TicketTierRepository tiers = mock(TicketTierRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderNumberGenerator orderNumbers = mock(OrderNumberGenerator.class);
    private final WaitingRoomService waitingRoom = mock(WaitingRoomService.class);
    private final WaitlistService waitlist = mock(WaitlistService.class);

    private OrderService service;

    @BeforeEach
    void setUp() {
        when(orderNumbers.next()).thenReturn("FT20260814ABCDEFGH");
        when(orders.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(orders.saveAndFlush(any(TicketOrder.class))).thenAnswer(call -> call.getArgument(0));
        when(inventory.tryReserve(anyLong(), anyInt())).thenReturn(true);
        when(waitlist.consumeOffer(anyLong(), anyLong(), anyInt())).thenReturn(Optional.empty());

        service = new OrderService(
                orders, tiers, inventory, gateway, payments, orderNumbers, waitingRoom, waitlist, properties(), clock);
    }

    @Nested
    @DisplayName("checkout")
    class Checkout {

        @Test
        @DisplayName("a retried request returns the original order instead of a second one")
        void is_idempotent() {
            TicketOrder original = anOrder(OrderStatus.PENDING_PAYMENT);
            when(orders.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

            TicketOrder result = service.checkout(USER_ID, TIER_ID, 2, "key-1");

            assertThat(result).isSameAs(original);
            verify(inventory, never()).tryReserve(anyLong(), anyInt());
        }

        @Test
        void holds_stock_and_opens_a_payment_window() {
            givenTier(EventStatus.ON_SALE);

            TicketOrder order = service.checkout(USER_ID, TIER_ID, 2, "key-1");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getQuantity()).isEqualTo(2);
            assertThat(order.getTotalCents()).isEqualTo(2 * 5_000);
            assertThat(order.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
            assertThat(order.getActiveLockKey()).isEqualTo(USER_ID + ":" + EVENT_ID);
            verify(inventory).tryReserve(TIER_ID, 2);
            verify(inventory).recordReservation(eq(TIER_ID), any(), eq(2));
        }

        @Test
        @DisplayName("running out of stock is reported as sold out, not as a crash")
        void rejects_when_stock_ran_out() {
            givenTier(EventStatus.ON_SALE);
            when(inventory.tryReserve(anyLong(), anyInt())).thenReturn(false);

            assertThatThrownBy(() -> service.checkout(USER_ID, TIER_ID, 1, "key-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.SOLD_OUT);
        }

        @Test
        void rejects_more_tickets_than_the_per_user_limit() {
            givenTier(EventStatus.ON_SALE);

            assertThatThrownBy(() -> service.checkout(USER_ID, TIER_ID, 9, "key-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
            verify(inventory, never()).tryReserve(anyLong(), anyInt());
        }

        @Test
        void rejects_events_that_are_not_on_sale() {
            givenTier(EventStatus.DRAFT);

            assertThatThrownBy(() -> service.checkout(USER_ID, TIER_ID, 1, "key-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.EVENT_NOT_ON_SALE);
            verify(inventory, never()).tryReserve(anyLong(), anyInt());
        }

        @Test
        void rejects_requests_before_the_sales_window_opens() {
            givenTier(EventStatus.ON_SALE, NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(30)));

            assertThatThrownBy(() -> service.checkout(USER_ID, TIER_ID, 1, "key-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.EVENT_NOT_ON_SALE);
        }

        @Test
        @DisplayName("a queue-jumper is turned away before stock is touched")
        void rejects_checkout_without_a_waiting_room_pass() {
            givenTier(EventStatus.ON_SALE);
            doThrow(new BusinessException(ErrorCode.WAITING_ROOM_TOKEN_REQUIRED, "Join the waiting room"))
                    .when(waitingRoom).requireAdmission(EVENT_ID, USER_ID, true);

            assertThatThrownBy(() -> service.checkout(USER_ID, TIER_ID, 1, "key-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.WAITING_ROOM_TOKEN_REQUIRED);
            verify(inventory, never()).tryReserve(anyLong(), anyInt());
        }

        @Test
        @DisplayName("a waitlist offer already holds the seats, so checkout must not take them again")
        void converting_an_offer_skips_a_second_reserve() {
            givenTier(EventStatus.SOLD_OUT);
            WaitlistEntry entry = WaitlistEntry.join(EVENT_ID, TIER_ID, USER_ID, 2, 1, NOW);
            entry.offer(NOW, NOW.plus(Duration.ofMinutes(15)));
            when(waitlist.consumeOffer(USER_ID, TIER_ID, 2)).thenReturn(Optional.of(entry));

            TicketOrder order = service.checkout(USER_ID, TIER_ID, 2, "key-1");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            verify(inventory, never()).tryReserve(anyLong(), anyInt());
            verify(inventory, never()).recordReservation(anyLong(), any(), anyInt());
            verify(waitlist).markConverted(eq(entry), any());
            verify(waitingRoom, never()).requireAdmission(anyLong(), anyLong(), anyBoolean());
        }

        @Test
        @DisplayName("the unique index, not the application, has the last word on duplicate orders")
        void maps_the_unique_index_violation_to_a_business_error() {
            givenTier(EventStatus.ON_SALE);
            when(orders.saveAndFlush(any(TicketOrder.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_orders_active_lock"));

            assertThatThrownBy(() -> service.checkout(USER_ID, TIER_ID, 1, "key-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.DUPLICATE_ACTIVE_ORDER);
            // The stock taken a moment earlier goes back when the transaction rolls back,
            // so the reservation must never reach the audit trail.
            verify(inventory, never()).recordReservation(anyLong(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("payment")
    class Paying {

        @Test
        void a_successful_charge_issues_the_tickets() {
            TicketOrder order = anOrder(OrderStatus.PENDING_PAYMENT);
            when(orders.findByOrderNo("FT-1")).thenReturn(Optional.of(order));
            when(gateway.charge(anyString(), anyInt()))
                    .thenReturn(new PaymentGateway.Charge("ref", Payment.Status.SUCCEEDED));

            TicketOrder result = service.pay(USER_ID, "FT-1");

            assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(result.getPaidAt()).isEqualTo(NOW);
            assertThat(result.getCompletedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("a declined charge is still recorded, so the attempt is auditable")
        void a_declined_charge_leaves_the_order_unpaid() {
            TicketOrder order = anOrder(OrderStatus.PENDING_PAYMENT);
            when(orders.findByOrderNo("FT-1")).thenReturn(Optional.of(order));
            when(gateway.charge(anyString(), anyInt()))
                    .thenReturn(new PaymentGateway.Charge("ref", Payment.Status.FAILED));

            assertThatThrownBy(() -> service.pay(USER_ID, "FT-1"))
                    .isInstanceOf(BusinessException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            verify(payments).save(any(Payment.class));
        }

        @Test
        @DisplayName("paying past the deadline expires the order and returns the seats")
        void an_overdue_order_cannot_be_paid() {
            TicketOrder order = anOrder(OrderStatus.PENDING_PAYMENT, NOW.minus(Duration.ofMinutes(1)));
            when(orders.findByOrderNo("FT-1")).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.pay(USER_ID, "FT-1"))
                    .isInstanceOf(BusinessException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            verify(inventory).release(anyLong(), anyInt(), any(), eq(InventoryLedgerEntry.Reason.RELEASE_EXPIRED));
            verify(gateway, never()).charge(anyString(), anyInt());
        }

        @Test
        @DisplayName("another user's order is reported as missing rather than forbidden")
        void hides_orders_belonging_to_someone_else() {
            when(orders.findByOrderNo("FT-1")).thenReturn(Optional.of(anOrder(OrderStatus.PENDING_PAYMENT)));

            assertThatThrownBy(() -> service.pay(999L, "FT-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        void cancelling_returns_the_stock() {
            TicketOrder order = anOrder(OrderStatus.PENDING_PAYMENT);
            when(orders.findByOrderNo("FT-1")).thenReturn(Optional.of(order));

            TicketOrder result = service.cancel(USER_ID, "FT-1");

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.getActiveLockKey()).isNull();
            verify(inventory).release(eq(TIER_ID), eq(2), any(), eq(InventoryLedgerEntry.Reason.RELEASE_CANCELLED));
            verify(waitlist).offerHead(TIER_ID);
        }

        @Test
        void the_sweeper_expires_every_overdue_order() {
            TicketOrder first = anOrder(OrderStatus.PENDING_PAYMENT, NOW.minus(Duration.ofMinutes(5)));
            TicketOrder second = anOrder(OrderStatus.CREATED, NOW.minus(Duration.ofMinutes(30)));
            when(orders.findExpired(any(), eq(NOW))).thenReturn(List.of(first, second));

            assertThat(service.expireOverdueOrders()).isEqualTo(2);
            assertThat(first.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(second.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            verify(waitlist, times(2)).offerHead(TIER_ID);
        }
    }

    private void givenTier(EventStatus status) {
        givenTier(status, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(30)), status == EventStatus.ON_SALE);
    }

    private void givenTier(EventStatus status, Instant salesStartAt, Instant salesEndAt) {
        givenTier(status, salesStartAt, salesEndAt, false);
    }

    private void givenTier(EventStatus status, Instant salesStartAt, Instant salesEndAt, boolean waitingRoomEnabled) {
        when(tiers.findPurchaseView(TIER_ID)).thenReturn(Optional.of(
                new TierPurchaseView(TIER_ID, EVENT_ID, 5_000, 4, status, salesStartAt, salesEndAt, waitingRoomEnabled)));
    }

    private TicketOrder anOrder(OrderStatus status) {
        return anOrder(status, NOW.plus(Duration.ofMinutes(10)));
    }

    private TicketOrder anOrder(OrderStatus status, Instant expiresAt) {
        TicketOrder order = TicketOrder.create(
                "FT-1", USER_ID, EVENT_ID, TIER_ID, 2, 5_000, "key-1", NOW, expiresAt);
        if (status != OrderStatus.CREATED) {
            order.transitionTo(status, NOW);
        }
        return order;
    }

    private static TicketingProperties properties() {
        return new TicketingProperties(
                new TicketingProperties.Inventory(InventoryStrategy.DB_PESSIMISTIC_LOCK),
                new TicketingProperties.Order(Duration.ofMinutes(10), 4),
                new TicketingProperties.Waitlist(Duration.ofMinutes(15)),
                new TicketingProperties.WaitingRoom(false, 20, 50, Duration.ofMinutes(5), 200, Duration.ofHours(12)),
                new TicketingProperties.Payment(0.0),
                new TicketingProperties.Security("test-secret-that-is-long-enough-32", Duration.ofHours(2)),
                new TicketingProperties.Seed(false, 0, 0, 0, 0, 0, 1L),
                new TicketingProperties.Cors(List.of("http://localhost:5173")),
                new TicketingProperties.Ml("http://127.0.0.1:9", Duration.ofSeconds(1), false));
    }
}
