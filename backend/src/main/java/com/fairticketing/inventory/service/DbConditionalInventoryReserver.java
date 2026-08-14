package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.springframework.stereotype.Component;

/**
 * No read, no lock held across a decision: a single UPDATE whose WHERE clause
 * refuses to take stock that is not there. Rows still contend inside InnoDB,
 * but the round trip is one statement instead of a lock-read-write cycle.
 */
@Component
public class DbConditionalInventoryReserver implements InventoryReserver {

    private final TicketTierRepository tiers;

    public DbConditionalInventoryReserver(TicketTierRepository tiers) {
        this.tiers = tiers;
    }

    @Override
    public boolean tryReserve(Long tierId, int quantity) {
        return tiers.tryReserve(tierId, quantity) == 1;
    }

    @Override
    public void release(Long tierId, int quantity) {
        tiers.release(tierId, quantity);
    }

    @Override
    public InventoryStrategy strategy() {
        return InventoryStrategy.DB_CONDITIONAL_UPDATE;
    }
}
