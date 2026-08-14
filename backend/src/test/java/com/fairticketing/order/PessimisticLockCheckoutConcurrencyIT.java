package com.fairticketing.order;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "ticketing.inventory.strategy=DB_PESSIMISTIC_LOCK")
class PessimisticLockCheckoutConcurrencyIT extends AbstractCheckoutConcurrencyIT {

    @Override
    protected InventoryStrategy expectedStrategy() {
        return InventoryStrategy.DB_PESSIMISTIC_LOCK;
    }
}
