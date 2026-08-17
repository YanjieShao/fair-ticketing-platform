package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.InventoryLedgerRepository;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private final InventoryReserver pessimistic = mock(InventoryReserver.class);
    private final InventoryLedgerRepository ledger = mock(InventoryLedgerRepository.class);
    private final TicketTierRepository tiers = mock(TicketTierRepository.class);
    private InventoryService service;

    @BeforeEach
    void setUp() {
        when(pessimistic.strategy()).thenReturn(InventoryStrategy.DB_PESSIMISTIC_LOCK);
        when(pessimistic.tryReserve(42L, 2)).thenReturn(true);
        service = new InventoryService(
                List.of(pessimistic),
                ledger,
                tiers,
                Fixtures.properties(),
                Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void reserve_delegates_to_the_configured_strategy_and_ledger_is_written_separately() {
        assertThat(service.tryReserve(42L, 2)).isTrue();
        verify(pessimistic).tryReserve(42L, 2);

        service.recordReservation(42L, 9L, 2);
        verify(ledger).save(any(InventoryLedgerEntry.class));
    }

    @Test
    void remaining_uses_the_live_counter_not_a_stale_row() {
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        when(pessimistic.remaining(tier)).thenReturn(7);
        when(tiers.findById(42L)).thenReturn(Optional.of(tier));

        assertThat(service.remaining(tier)).isEqualTo(7);
        assertThat(service.remaining(42L)).isEqualTo(7);
    }

    @Test
    void remaining_rejects_an_unknown_tier() {
        when(tiers.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remaining(99L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void release_hands_stock_back_and_records_a_negative_delta() {
        service.release(42L, 2, 9L, InventoryLedgerEntry.Reason.RELEASE_EXPIRED);
        verify(pessimistic).release(42L, 2);
        verify(ledger).save(any(InventoryLedgerEntry.class));
    }

    @Test
    void missing_strategy_is_a_programming_error() {
        InventoryService empty = new InventoryService(
                List.of(), ledger, tiers, Fixtures.properties(), Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> empty.tryReserve(1L, 1)).isInstanceOf(IllegalStateException.class);
    }
}
