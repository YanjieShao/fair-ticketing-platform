package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.InventoryLedgerRepository;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReconciliationServiceTest {

    @Test
    void ledger_wins_when_the_tier_row_has_drifted() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        InventoryLedgerRepository ledger = mock(InventoryLedgerRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        RedisLuaInventoryReserver redis = mock(RedisLuaInventoryReserver.class);
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        tier.setReservedQuantity(8);

        when(ledger.netDeltaForTier(42L)).thenReturn(10);
        when(tiers.findByIdForUpdate(42L)).thenReturn(Optional.of(tier));
        when(inventory.activeStrategy()).thenReturn(InventoryStrategy.DB_PESSIMISTIC_LOCK);

        InventoryReconciliationService service = new InventoryReconciliationService(tiers, ledger, inventory, redis);
        var result = service.reconcileTier(42L);

        assertThat(tier.getReservedQuantity()).isEqualTo(10);
        assertThat(result.agreed()).isFalse();
        assertThat(result.fromLedger()).isEqualTo(10);
        assertThat(result.redisCounter()).isNull();
    }

    @Test
    void redis_is_only_reported_when_that_strategy_is_on() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        InventoryLedgerRepository ledger = mock(InventoryLedgerRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        RedisLuaInventoryReserver redis = mock(RedisLuaInventoryReserver.class);
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        tier.setReservedQuantity(10);

        when(ledger.netDeltaForTier(42L)).thenReturn(10);
        when(tiers.findByIdForUpdate(42L)).thenReturn(Optional.of(tier));
        when(inventory.activeStrategy()).thenReturn(InventoryStrategy.REDIS_LUA);
        when(redis.counter(42L)).thenReturn(90);

        InventoryReconciliationService service = new InventoryReconciliationService(tiers, ledger, inventory, redis);
        var result = service.reconcileTier(42L);

        assertThat(result.agreed()).isTrue();
        assertThat(result.redisCounter()).isEqualTo(90);
        assertThat(result.redisExpected()).isEqualTo(90);
    }

    @Test
    void missing_tier_fails_closed() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        when(tiers.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        InventoryReconciliationService service = new InventoryReconciliationService(
                tiers, mock(InventoryLedgerRepository.class), mock(InventoryService.class),
                mock(RedisLuaInventoryReserver.class));
        assertThatThrownBy(() -> service.reconcileTier(99L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void scheduled_pass_walks_every_selling_tier() {
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        InventoryLedgerRepository ledger = mock(InventoryLedgerRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        TicketTier tier = Fixtures.standing(Fixtures.onSaleEvent(5L), 42L);
        when(tiers.findIdsByEventStatuses(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(42L));
        when(ledger.netDeltaForTier(42L)).thenReturn(10);
        when(tiers.findByIdForUpdate(42L)).thenReturn(Optional.of(tier));
        when(inventory.activeStrategy()).thenReturn(InventoryStrategy.DB_PESSIMISTIC_LOCK);

        new InventoryReconciliationService(tiers, ledger, inventory, mock(RedisLuaInventoryReserver.class))
                .reconcileTiersOnSale();
        verify(tiers).findByIdForUpdate(42L);
    }
}
