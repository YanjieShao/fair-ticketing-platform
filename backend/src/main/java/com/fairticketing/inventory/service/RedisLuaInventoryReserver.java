package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.InventoryLedgerRepository;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Keeps the contended counter in Redis instead of a database row.
 *
 * <p>The point is what this does <em>not</em> do: it never takes an exclusive
 * lock on the tier row, so buyers stop queueing behind each other in InnoDB.
 * The durable record of each hold is the order row and its ledger entry, both
 * of which are per-order inserts and do not contend. The tier's stored
 * quantity is brought back in line by {@link InventoryReconciliationService}.
 */
@Component
public class RedisLuaInventoryReserver implements InventoryReserver {

    private static final String KEY_PREFIX = "ticketing:tier:";
    private static final long COUNTER_MISSING = -1L;

    private final StringRedisTemplate redis;
    private final TicketTierRepository tiers;
    private final InventoryLedgerRepository ledger;
    private final RedisScript<Long> reserveScript;
    private final RedisScript<Long> releaseScript;

    public RedisLuaInventoryReserver(StringRedisTemplate redis,
                                     TicketTierRepository tiers,
                                     InventoryLedgerRepository ledger) {
        this.redis = redis;
        this.tiers = tiers;
        this.ledger = ledger;
        this.reserveScript = script("redis/reserve_tickets.lua");
        this.releaseScript = script("redis/release_tickets.lua");
    }

    @Override
    public boolean tryReserve(Long tierId, int quantity) {
        Long result = run(reserveScript, tierId, String.valueOf(quantity));
        if (result == COUNTER_MISSING) {
            loadCounter(tierId);
            result = run(reserveScript, tierId, String.valueOf(quantity));
        }
        return result != null && result == 1L;
    }

    @Override
    public void release(Long tierId, int quantity) {
        TicketTier tier = tier(tierId);
        Long result = run(releaseScript, tierId,
                String.valueOf(quantity), String.valueOf(tier.getTotalQuantity()));

        if (result != null && result == COUNTER_MISSING) {
            // Loading rebuilds the counter from the ledger, which does not yet
            // record this release, so it still has to be applied afterwards.
            loadCounter(tierId);
            run(releaseScript, tierId, String.valueOf(quantity), String.valueOf(tier.getTotalQuantity()));
        }
    }

    @Override
    public int remaining(TicketTier tier) {
        Integer counter = counter(tier.getId());
        if (counter != null) {
            return counter;
        }
        loadCounter(tier.getId());
        Integer loaded = counter(tier.getId());
        return loaded != null ? loaded : 0;
    }

    /** The live counter, or null when Redis has never been told about this tier. */
    public Integer counter(Long tierId) {
        String value = redis.opsForValue().get(key(tierId));
        return value == null ? null : Integer.valueOf(value);
    }

    public void loadCounter(Long tierId) {
        TicketTier tier = tier(tierId);
        int held = ledger.netDeltaForTier(tierId);
        // Rebuilt from the ledger rather than the tier's stored quantity, which
        // reconciliation may not have caught up with yet.
        redis.opsForValue().setIfAbsent(key(tierId), String.valueOf(tier.getTotalQuantity() - held));
    }

    public void resetCounter(Long tierId, int remaining) {
        redis.opsForValue().set(key(tierId), String.valueOf(remaining));
    }

    @Override
    public InventoryStrategy strategy() {
        return InventoryStrategy.REDIS_LUA;
    }

    private Long run(RedisScript<Long> script, Long tierId, String... args) {
        return redis.execute(script, List.of(key(tierId)), (Object[]) args);
    }

    private TicketTier tier(Long tierId) {
        return tiers.findById(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));
    }

    private static String key(Long tierId) {
        // Deliberately no expiry: a counter that vanishes mid-sale would be
        // rebuilt from data that is still catching up.
        return KEY_PREFIX + tierId + ":remaining";
    }

    private static RedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }
}
