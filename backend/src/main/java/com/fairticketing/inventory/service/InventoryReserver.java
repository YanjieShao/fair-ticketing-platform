package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;

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

    InventoryStrategy strategy();
}
