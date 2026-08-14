package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.inventory.domain.TicketTier;

/**
 * Holds and returns stock for one ticket tier.
 *
 * <p>Implementations must never allow the reserved quantity to exceed the total,
 * no matter how many buyers arrive at once. Running out of stock is an ordinary
 * outcome and is reported by returning false, not by throwing.
 */
public interface InventoryReserver {

    /**
     * @return true if the whole quantity was held, false if there was not enough left
     */
    boolean tryReserve(Long tierId, int quantity);

    void release(Long tierId, int quantity);

    /**
     * How many are left according to whatever this implementation treats as the
     * live count, which is not always the number stored on the tier row. Takes
     * the loaded tier so a listing does not re-query once per row.
     */
    int remaining(TicketTier tier);

    InventoryStrategy strategy();
}
