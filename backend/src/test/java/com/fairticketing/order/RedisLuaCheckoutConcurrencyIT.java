package com.fairticketing.order;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "ticketing.inventory.strategy=REDIS_LUA")
class RedisLuaCheckoutConcurrencyIT extends AbstractCheckoutConcurrencyIT {

    @Override
    protected InventoryStrategy expectedStrategy() {
        return InventoryStrategy.REDIS_LUA;
    }
}
