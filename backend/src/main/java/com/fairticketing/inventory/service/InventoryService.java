package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.InventoryLedgerRepository;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Chooses the configured reserver and records every movement in the ledger, so
 * swapping strategies changes only how stock is held, never how it is audited.
 */
@Service
public class InventoryService {

    private final Map<InventoryStrategy, InventoryReserver> reservers = new EnumMap<>(InventoryStrategy.class);
    private final InventoryLedgerRepository ledger;
    private final TicketTierRepository tiers;
    private final TicketingProperties properties;
    private final Clock clock;

    public InventoryService(List<InventoryReserver> available,
                            InventoryLedgerRepository ledger,
                            TicketTierRepository tiers,
                            TicketingProperties properties,
                            Clock clock) {
        available.forEach(reserver -> reservers.put(reserver.strategy(), reserver));
        this.ledger = ledger;
        this.tiers = tiers;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Moves the stock. Auditing is a separate call because the order row does
     * not exist yet at this point: taking the tier lock before inserting the
     * order is what keeps the lock order the same for every buyer, and an
     * order row cannot be inserted without first taking a shared lock on the
     * tier it references.
     *
     * <p>The pair cannot drift apart because both run inside the checkout
     * transaction, so anything that skips the second call rolls back the first.
     */
    public boolean tryReserve(Long tierId, int quantity) {
        return active().tryReserve(tierId, quantity);
    }

    public void recordReservation(Long tierId, Long orderId, int quantity) {
        writeLedger(tierId, orderId, quantity, InventoryLedgerEntry.Reason.RESERVE);
    }

    public void recordHold(Long tierId, int quantity, InventoryLedgerEntry.Reason reason) {
        writeLedger(tierId, null, quantity, reason);
    }

    public void release(Long tierId, int quantity, Long orderId, InventoryLedgerEntry.Reason reason) {
        active().release(tierId, quantity);
        writeLedger(tierId, orderId, -quantity, reason);
    }

    /**
     * The live count, which under the Redis strategy is not the number stored
     * on the tier row: that one trails behind until reconciliation catches it up,
     * and showing it would tell buyers there are seats left after they have gone.
     */
    public int remaining(TicketTier tier) {
        return active().remaining(tier);
    }

    public int remaining(Long tierId) {
        return remaining(tiers.findById(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found")));
    }

    public InventoryStrategy activeStrategy() {
        return properties.inventory().strategy();
    }

    private InventoryReserver active() {
        InventoryStrategy strategy = activeStrategy();
        InventoryReserver reserver = reservers.get(strategy);
        if (reserver == null) {
            throw new IllegalStateException("No inventory reserver available for strategy " + strategy);
        }
        return reserver;
    }

    private void writeLedger(Long tierId, Long orderId, int delta, InventoryLedgerEntry.Reason reason) {
        ledger.save(new InventoryLedgerEntry(tierId, orderId, delta, reason, Instant.now(clock)));
    }
}
