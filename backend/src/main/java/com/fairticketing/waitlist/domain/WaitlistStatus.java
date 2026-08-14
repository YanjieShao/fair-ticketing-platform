package com.fairticketing.waitlist.domain;

import com.fairticketing.common.domain.IllegalStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Waitlist lifecycle, separate from the waiting room used before an event
 * sells out. Entries are served strictly first-in-first-out; when inventory is
 * released the head of the queue gets an exclusive, time-boxed offer.
 */
public enum WaitlistStatus {

    /** Queued, waiting for inventory to be released. */
    WAITING,
    /** Holds an exclusive purchase window. */
    OFFERED,
    /** Bought within the offer window. Terminal. */
    CONVERTED,
    /** Offer window elapsed, the next entry is served instead. Terminal. */
    OFFER_EXPIRED,
    /** Left the queue. Terminal. */
    CANCELLED;

    private static final Map<WaitlistStatus, Set<WaitlistStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(WaitlistStatus.class);
        ALLOWED.put(WAITING, EnumSet.of(OFFERED, CANCELLED));
        ALLOWED.put(OFFERED, EnumSet.of(CONVERTED, OFFER_EXPIRED, CANCELLED));
        ALLOWED.put(CONVERTED, EnumSet.noneOf(WaitlistStatus.class));
        ALLOWED.put(OFFER_EXPIRED, EnumSet.noneOf(WaitlistStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(WaitlistStatus.class));
    }

    public boolean canTransitionTo(WaitlistStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public WaitlistStatus transitionTo(WaitlistStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateTransitionException("WaitlistEntry", this, target);
        }
        return target;
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** Entries that still compete for released inventory. */
    public boolean isActive() {
        return this == WAITING || this == OFFERED;
    }

    public Set<WaitlistStatus> allowedTargets() {
        return EnumSet.copyOf(ALLOWED.get(this));
    }
}
