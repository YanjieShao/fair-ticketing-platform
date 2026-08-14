package com.fairticketing.waitlist.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.repository.TierPurchaseView;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.notification.service.NotificationService;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.domain.WaitlistStatus;
import com.fairticketing.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final Long USER_ID = 7L;
    private static final Long TIER_ID = 42L;
    private static final Long EVENT_ID = 5L;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
    private final TicketTierRepository tiers = mock(TicketTierRepository.class);
    private final TicketOrderRepository orders = mock(TicketOrderRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final NotificationService notifications = mock(NotificationService.class);

    private WaitlistService waitlist;

    @BeforeEach
    void setUp() {
        when(entries.saveAndFlush(any(WaitlistEntry.class))).thenAnswer(call -> call.getArgument(0));
        when(entries.maxPositionSeq(TIER_ID)).thenReturn(3L);
        when(tiers.findByIdForUpdate(TIER_ID)).thenReturn(Optional.of(mock()));
        when(orders.findByActiveLockKey(anyString())).thenReturn(Optional.empty());

        waitlist = new WaitlistService(
                entries, tiers, orders, inventory, notifications, properties(), clock);
    }

    @Test
    @DisplayName("joining is refused while the tier still has enough seats")
    void refuses_when_tickets_are_still_on_sale() {
        givenTier(EventStatus.ON_SALE);
        when(inventory.remaining(TIER_ID)).thenReturn(8);

        assertThatThrownBy(() -> waitlist.join(USER_ID, TIER_ID, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.WAITLIST_NOT_NEEDED);
        verify(entries, never()).saveAndFlush(any());
    }

    @Test
    void assigns_the_next_fifo_position() {
        givenTier(EventStatus.ON_SALE);
        when(inventory.remaining(TIER_ID)).thenReturn(0);

        WaitlistEntry entry = waitlist.join(USER_ID, TIER_ID, 2);

        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(entry.getPositionSeq()).isEqualTo(4);
        assertThat(entry.getActiveLockKey()).isEqualTo(USER_ID + ":" + TIER_ID);
    }

    @Test
    void a_second_live_place_is_the_database_constraint() {
        givenTier(EventStatus.SOLD_OUT);
        when(inventory.remaining(TIER_ID)).thenReturn(0);
        when(entries.saveAndFlush(any(WaitlistEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uk_waitlist_active_lock"));

        assertThatThrownBy(() -> waitlist.join(USER_ID, TIER_ID, 1))
                .extracting("code").isEqualTo(ErrorCode.ALREADY_ON_WAITLIST);
    }

    @Test
    @DisplayName("returned stock is held for the head of the line, not dumped on sale")
    void offer_holds_stock_for_the_fifo_head() {
        WaitlistEntry head = WaitlistEntry.join(EVENT_ID, TIER_ID, USER_ID, 2, 1, NOW);
        when(entries.findFirstByTierIdAndStatusOrderByPositionSeqAsc(TIER_ID, WaitlistStatus.WAITING))
                .thenReturn(Optional.of(head))
                .thenReturn(Optional.empty());
        when(inventory.remaining(TIER_ID)).thenReturn(2);
        when(inventory.tryReserve(TIER_ID, 2)).thenReturn(true);

        waitlist.offerHead(TIER_ID);

        assertThat(head.getStatus()).isEqualTo(WaitlistStatus.OFFERED);
        assertThat(head.getOfferExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        verify(inventory).recordHold(TIER_ID, 2, InventoryLedgerEntry.Reason.RESERVE_OFFER);
        verify(notifications).notifyUser(eq(USER_ID), eq("WAITLIST_OFFER"), anyString(), anyString(), anyString());
    }

    @Test
    void a_shortfall_leaves_the_head_waiting() {
        WaitlistEntry head = WaitlistEntry.join(EVENT_ID, TIER_ID, USER_ID, 4, 1, NOW);
        when(entries.findFirstByTierIdAndStatusOrderByPositionSeqAsc(TIER_ID, WaitlistStatus.WAITING))
                .thenReturn(Optional.of(head));
        when(inventory.remaining(TIER_ID)).thenReturn(2);

        waitlist.offerHead(TIER_ID);

        assertThat(head.getStatus()).isEqualTo(WaitlistStatus.WAITING);
        verify(inventory, never()).tryReserve(anyLong(), anyInt());
    }

    @Test
    void an_expired_offer_cannot_be_converted() {
        WaitlistEntry entry = WaitlistEntry.join(EVENT_ID, TIER_ID, USER_ID, 2, 1, NOW);
        entry.offer(NOW.minus(Duration.ofMinutes(20)), NOW.minus(Duration.ofMinutes(5)));
        when(entries.findByUserIdAndTierIdAndStatus(USER_ID, TIER_ID, WaitlistStatus.OFFERED))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> waitlist.consumeOffer(USER_ID, TIER_ID, 2))
                .extracting("code").isEqualTo(ErrorCode.OFFER_WINDOW_CLOSED);
        assertThat(entry.getStatus()).isEqualTo(WaitlistStatus.OFFERED);
        verify(inventory, never()).release(anyLong(), anyInt(), any(), any());
    }

    private void givenTier(EventStatus status) {
        when(tiers.findPurchaseView(TIER_ID)).thenReturn(Optional.of(
                new TierPurchaseView(TIER_ID, EVENT_ID, 5_000, 4, status,
                        NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(30)))));
    }

    private static TicketingProperties properties() {
        return new TicketingProperties(
                new TicketingProperties.Inventory(InventoryStrategy.DB_PESSIMISTIC_LOCK),
                new TicketingProperties.Order(Duration.ofMinutes(10), 4),
                new TicketingProperties.Waitlist(Duration.ofMinutes(15)),
                new TicketingProperties.WaitingRoom(false, 20, 50, Duration.ofMinutes(5), 200, Duration.ofHours(12)),
                new TicketingProperties.Payment(0.0),
                new TicketingProperties.Security("test-secret-that-is-long-enough-32", Duration.ofHours(2)),
                new TicketingProperties.Seed(false, 0, 0, 0, 0, 0, 1L));
    }
}
