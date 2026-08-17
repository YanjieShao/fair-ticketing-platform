package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReserverTest {

    @Test
    void pessimistic_lock_refuses_when_the_row_cannot_cover_the_request() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        tier.setReservedQuantity(99);
        when(tiers.findByIdForUpdate(42L)).thenReturn(Optional.of(tier));

        DbPessimisticInventoryReserver reserver = new DbPessimisticInventoryReserver(tiers);
        assertThat(reserver.tryReserve(42L, 2)).isFalse();
        assertThat(reserver.strategy()).isEqualTo(InventoryStrategy.DB_PESSIMISTIC_LOCK);
        assertThat(reserver.remaining(tier)).isEqualTo(1);
    }

    @Test
    void pessimistic_lock_increments_reserved_quantity_when_stock_is_there() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        when(tiers.findByIdForUpdate(42L)).thenReturn(Optional.of(tier));

        DbPessimisticInventoryReserver reserver = new DbPessimisticInventoryReserver(tiers);
        assertThat(reserver.tryReserve(42L, 2)).isTrue();
        assertThat(tier.getReservedQuantity()).isEqualTo(12);

        reserver.release(42L, 2);
        assertThat(tier.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    void pessimistic_lock_fails_closed_when_the_tier_is_gone() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        when(tiers.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        DbPessimisticInventoryReserver reserver = new DbPessimisticInventoryReserver(tiers);
        assertThatThrownBy(() -> reserver.tryReserve(42L, 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reserver.release(42L, 1)).isInstanceOf(BusinessException.class);
    }

    @Test
    void conditional_update_treats_one_updated_row_as_success() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        when(tiers.tryReserve(42L, 2)).thenReturn(1);
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);

        DbConditionalInventoryReserver reserver = new DbConditionalInventoryReserver(tiers);
        assertThat(reserver.tryReserve(42L, 2)).isTrue();
        when(tiers.tryReserve(42L, 2)).thenReturn(0);
        assertThat(reserver.tryReserve(42L, 2)).isFalse();
        reserver.release(42L, 2);
        verify(tiers).release(42L, 2);
        assertThat(reserver.remaining(tier)).isEqualTo(90);
        assertThat(reserver.strategy()).isEqualTo(InventoryStrategy.DB_CONDITIONAL_UPDATE);
    }
}
