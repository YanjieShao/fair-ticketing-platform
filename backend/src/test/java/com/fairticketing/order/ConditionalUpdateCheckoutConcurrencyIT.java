package com.fairticketing.order;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "ticketing.inventory.strategy=DB_CONDITIONAL_UPDATE")
class ConditionalUpdateCheckoutConcurrencyIT extends AbstractCheckoutConcurrencyIT {

    @Override
    protected InventoryStrategy expectedStrategy() {
        return InventoryStrategy.DB_CONDITIONAL_UPDATE;
    }
}
