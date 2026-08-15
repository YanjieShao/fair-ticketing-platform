package com.fairticketing.inventory.service;

import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.InventoryLedgerRepository;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Keeps the stored quantity honest against the ledger, and reports when Redis
 * and the database disagree.
 *
 * <p>Under the Redis strategy the hot path never writes the tier row, so that
 * column drifts by design; the ledger is what actually happened and this is what
 * folds it back in. Redis itself is only <em>reported</em> on, never rewritten:
 * overwriting a counter that live buyers are decrementing would erase the
 * reservations made between reading the ledger and writing the new value, which
 * is precisely the overselling this system exists to prevent.
 */
@Service
public class InventoryReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationService.class);
    private static final Set<EventStatus> SELLING = Set.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT);

    private final TicketTierRepository tiers;
    private final InventoryLedgerRepository ledger;
    private final InventoryService inventory;
    private final RedisLuaInventoryReserver redisReserver;

    public InventoryReconciliationService(TicketTierRepository tiers,
                                          InventoryLedgerRepository ledger,
                                          InventoryService inventory,
                                          RedisLuaInventoryReserver redisReserver) {
        this.tiers = tiers;
        this.ledger = ledger;
        this.inventory = inventory;
        this.redisReserver = redisReserver;
    }

    @Transactional
    public Reconciliation reconcileTier(Long tierId) {
        int fromLedger = ledger.netDeltaForTier(tierId);
        TicketTier tier = tiers.findByIdForUpdate(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));

        int stored = tier.getReservedQuantity();
        if (stored != fromLedger) {
            tier.setReservedQuantity(fromLedger);
        }

        Integer redisCounter = inventory.activeStrategy() == InventoryStrategy.REDIS_LUA
                ? redisReserver.counter(tierId)
                : null;

        Reconciliation result = new Reconciliation(
                tierId, stored, fromLedger, redisCounter, tier.getTotalQuantity() - fromLedger);

        if (!result.agreed()) {
            log.warn("Inventory drift on tier {}: stored={}, ledger={}, redis={}",
                    tierId, stored, fromLedger, redisCounter);
        }
        return result;
    }

    @Scheduled(fixedDelayString = "${ticketing.inventory.reconcile-interval:PT10S}")
    @Transactional
    public void reconcileTiersOnSale() {
        List<Long> tierIds = tiers.findIdsByEventStatuses(SELLING);
        tierIds.forEach(this::reconcileTier);
    }

    /**
     * @param storedBefore  what the tier row claimed
     * @param fromLedger    what the ledger says was actually taken
     * @param redisCounter  the live counter, or null when Redis is not in use
     * @param redisExpected what that counter should read
     */
    public record Reconciliation(Long tierId, int storedBefore, int fromLedger,
                                 Integer redisCounter, int redisExpected) {

        public boolean agreed() {
            return storedBefore == fromLedger && (redisCounter == null || redisCounter == redisExpected);
        }
    }
}
