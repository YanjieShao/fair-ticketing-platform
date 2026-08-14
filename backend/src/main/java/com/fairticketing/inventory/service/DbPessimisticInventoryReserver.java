package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.springframework.stereotype.Component;

/**
 * Takes a row lock, reads, decides, writes. This is the baseline the other
 * strategies are measured against: every buyer for a tier queues behind the
 * same row, so throughput is bounded by how fast the database can hand the lock
 * around.
 */
@Component
public class DbPessimisticInventoryReserver implements InventoryReserver {

    private final TicketTierRepository tiers;

    public DbPessimisticInventoryReserver(TicketTierRepository tiers) {
        this.tiers = tiers;
    }

    @Override
    public boolean tryReserve(Long tierId, int quantity) {
        TicketTier tier = tiers.findByIdForUpdate(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));

        if (tier.availableQuantity() < quantity) {
            return false;
        }
        tier.setReservedQuantity(tier.getReservedQuantity() + quantity);
        return true;
    }

    @Override
    public void release(Long tierId, int quantity) {
        TicketTier tier = tiers.findByIdForUpdate(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));

        tier.setReservedQuantity(Math.max(0, tier.getReservedQuantity() - quantity));
    }

    @Override
    public InventoryStrategy strategy() {
        return InventoryStrategy.DB_PESSIMISTIC_LOCK;
    }
}
