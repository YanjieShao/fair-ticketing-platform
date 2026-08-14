package com.fairticketing.event.domain;

import com.fairticketing.common.domain.IllegalStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Event lifecycle. SOLD_OUT is reversible: cancelled and expired orders return
 * inventory, which puts the event back on sale.
 *
 * <p>Only events that have not started selling can be cancelled, which keeps
 * bulk refunds out of scope.
 */
public enum EventStatus {

    DRAFT,
    ON_SALE,
    SOLD_OUT,
    /** Sales window closed. Terminal. */
    CLOSED,
    /** Called off before going on sale. Terminal. */
    CANCELLED;

    private static final Map<EventStatus, Set<EventStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(EventStatus.class);
        ALLOWED.put(DRAFT, EnumSet.of(ON_SALE, CANCELLED));
        ALLOWED.put(ON_SALE, EnumSet.of(SOLD_OUT, CLOSED));
        ALLOWED.put(SOLD_OUT, EnumSet.of(ON_SALE, CLOSED));
        ALLOWED.put(CLOSED, EnumSet.noneOf(EventStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(EventStatus.class));
    }

    public boolean canTransitionTo(EventStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public EventStatus transitionTo(EventStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Event", this, target);
        }
        return target;
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** Whether checkout requests are accepted at all. */
    public boolean acceptsPurchases() {
        return this == ON_SALE;
    }

    public Set<EventStatus> allowedTargets() {
        return EnumSet.copyOf(ALLOWED.get(this));
    }
}
