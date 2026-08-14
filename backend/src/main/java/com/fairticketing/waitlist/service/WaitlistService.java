package com.fairticketing.waitlist.service;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.repository.TierPurchaseView;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.notification.service.NotificationService;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.domain.WaitlistStatus;
import com.fairticketing.waitlist.repository.WaitlistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The queue that starts after an event sells out. Places are served in join
 * order; returned stock is held for the head of the line for a short exclusive
 * window rather than dumped back onto the public sale.
 */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistEntryRepository entries;
    private final TicketTierRepository tiers;
    private final TicketOrderRepository orders;
    private final InventoryService inventory;
    private final NotificationService notifications;
    private final TicketingProperties properties;
    private final Clock clock;

    public WaitlistService(WaitlistEntryRepository entries,
                           TicketTierRepository tiers,
                           TicketOrderRepository orders,
                           InventoryService inventory,
                           NotificationService notifications,
                           TicketingProperties properties,
                           Clock clock) {
        this.entries = entries;
        this.tiers = tiers;
        this.orders = orders;
        this.inventory = inventory;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public WaitlistEntry join(Long userId, Long tierId, int quantity) {
        TierPurchaseView tier = tiers.findPurchaseView(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));

        if (!tier.eventStatus().acceptsPurchases() && tier.eventStatus() != EventStatus.SOLD_OUT) {
            throw new BusinessException(ErrorCode.EVENT_NOT_ON_SALE, "This event is not taking a waitlist");
        }

        int limit = Math.min(tier.maxPerUser(), properties.order().maxTicketsPerUserPerTier());
        if (quantity < 1 || quantity > limit) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED,
                    "You can waitlist at most " + limit + " tickets in this tier");
        }

        if (inventory.remaining(tierId) >= quantity) {
            throw new BusinessException(ErrorCode.WAITLIST_NOT_NEEDED,
                    "Tickets are still available in this tier");
        }

        if (orders.findByActiveLockKey(userId + ":" + tier.eventId()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_ORDER,
                    "You already have an order in progress for this event");
        }

        // Serialises position assignment on the tier row, the same row checkout
        // already contends on, so we do not invent a second lock order.
        tiers.findByIdForUpdate(tierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Ticket tier " + tierId + " not found"));

        long position = entries.maxPositionSeq(tierId) + 1;
        WaitlistEntry entry = WaitlistEntry.join(
                tier.eventId(), tierId, userId, quantity, position, Instant.now(clock));

        try {
            return entries.saveAndFlush(entry);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorCode.ALREADY_ON_WAITLIST,
                    "You are already on the waitlist for this tier");
        }
    }

    @Transactional
    public WaitlistEntry leave(Long userId, Long entryId) {
        WaitlistEntry entry = loadOwned(userId, entryId);
        boolean heldOffer = entry.getStatus() == WaitlistStatus.OFFERED;
        int quantity = entry.getRequestedQuantity();
        Long tierId = entry.getTierId();

        entry.cancel();

        if (heldOffer) {
            inventory.release(tierId, quantity, null, InventoryLedgerEntry.Reason.RELEASE_OFFER_CANCELLED);
            offerHead(tierId);
        }
        return entry;
    }

    /**
     * Called after stock is handed back. Holds as many consecutive heads as the
     * returned quantity can fill; leftover seats smaller than the next request
     * go back on public sale.
     */
    @Transactional
    public void offerHead(Long tierId) {
        while (true) {
            Optional<WaitlistEntry> head = entries.findFirstByTierIdAndStatusOrderByPositionSeqAsc(
                    tierId, WaitlistStatus.WAITING);
            if (head.isEmpty()) {
                return;
            }

            WaitlistEntry entry = head.get();
            int wanted = entry.getRequestedQuantity();
            if (inventory.remaining(tierId) < wanted) {
                return;
            }
            if (!inventory.tryReserve(tierId, wanted)) {
                return;
            }

            Instant now = Instant.now(clock);
            entry.offer(now, now.plus(properties.waitlist().offerWindow()));
            inventory.recordHold(tierId, wanted, InventoryLedgerEntry.Reason.RESERVE_OFFER);
            notifications.notifyUser(
                    entry.getUserId(),
                    "WAITLIST_OFFER",
                    "A ticket is being held for you",
                    "You have " + properties.waitlist().offerWindow().toMinutes()
                            + " minutes to buy " + wanted + " ticket(s) before they pass to the next person.",
                    "WAITLIST_OFFER:" + entry.getId());
            log.info("Offered waitlist entry {} on tier {}", entry.getId(), tierId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int expireOverdueOffers() {
        Instant now = Instant.now(clock);
        List<WaitlistEntry> overdue = entries.findExpiredOffers(now);
        for (WaitlistEntry entry : overdue) {
            expireOffer(entry, now);
        }
        if (!overdue.isEmpty()) {
            log.info("Expired {} waitlist offers and returned their inventory", overdue.size());
        }
        return overdue.size();
    }

    /**
     * The checkout path asks this before taking new stock: a live offer already
     * holds the seats, and taking them again would oversell.
     */
    @Transactional
    public Optional<WaitlistEntry> consumeOffer(Long userId, Long tierId, int quantity) {
        Optional<WaitlistEntry> found = entries.findByUserIdAndTierIdAndStatus(
                userId, tierId, WaitlistStatus.OFFERED);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        WaitlistEntry entry = found.get();
        if (entry.isOfferExpiredAt(Instant.now(clock))) {
            throw new BusinessException(ErrorCode.OFFER_WINDOW_CLOSED,
                    "The purchase window for this waitlist offer has closed");
        }
        if (entry.getRequestedQuantity() != quantity) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Buy the " + entry.getRequestedQuantity() + " ticket(s) being held for you");
        }
        return Optional.of(entry);
    }

    @Transactional
    public void markConverted(WaitlistEntry entry, Long orderId) {
        entry.convert(orderId);
    }

    @Transactional(readOnly = true)
    public WaitlistEntry findOwned(Long userId, Long entryId) {
        return loadOwned(userId, entryId);
    }

    public long peopleAhead(WaitlistEntry entry) {
        if (entry.getStatus() != WaitlistStatus.WAITING) {
            return 0;
        }
        return entries.countByTierIdAndStatusAndPositionSeqLessThan(
                entry.getTierId(), WaitlistStatus.WAITING, entry.getPositionSeq());
    }

    private void expireOffer(WaitlistEntry entry, Instant now) {
        Long tierId = entry.getTierId();
        int quantity = entry.getRequestedQuantity();
        entry.expireOffer();
        inventory.release(tierId, quantity, null, InventoryLedgerEntry.Reason.RELEASE_OFFER_EXPIRED);
        offerHead(tierId);
    }

    private WaitlistEntry loadOwned(Long userId, Long entryId) {
        WaitlistEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Waitlist entry " + entryId + " not found"));
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Waitlist entry " + entryId + " not found");
        }
        return entry;
    }
}
