package com.fairticketing.order.domain;

import com.fairticketing.common.domain.IllegalStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. The source document only listed the happy path
 * (CREATED to COMPLETED); EXPIRED and CANCELLED are required because unpaid
 * orders must release their inventory and users may cancel before paying.
 *
 * <p>A completed order can still be cancelled: that is a mock return (restock
 * plus waitlist), not a card refund. Real money never moved.
 */
public enum OrderStatus {

    /** Reserved inventory, user has not been sent to payment yet. */
    CREATED,
    /** Awaiting payment confirmation, still holding inventory until expiry. */
    PENDING_PAYMENT,
    /** Payment confirmed. */
    PAID,
    /** Tickets issued. Can still be returned (mock refund) to CANCELLED. */
    COMPLETED,
    /** Payment window elapsed, inventory released. Terminal. */
    EXPIRED,
    /** Cancelled by the user (unpaid hold or mock return), inventory released. Terminal. */
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED;

    static {
        ALLOWED = new EnumMap<>(OrderStatus.class);
        ALLOWED.put(CREATED, EnumSet.of(PENDING_PAYMENT, CANCELLED, EXPIRED));
        ALLOWED.put(PENDING_PAYMENT, EnumSet.of(PAID, CANCELLED, EXPIRED));
        ALLOWED.put(PAID, EnumSet.of(COMPLETED, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.of(CANCELLED));
        ALLOWED.put(EXPIRED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public OrderStatus transitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Order", this, target);
        }
        return target;
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** Only unpaid holds time out. A completed order keeps its seats until returned. */
    public boolean canExpire() {
        return this == CREATED || this == PENDING_PAYMENT;
    }

    /**
     * Whether an order in this state still counts against a tier's reserved
     * quantity and the per-user per-tier purchase cap.
     */
    public boolean occupiesInventory() {
        return this == CREATED || this == PENDING_PAYMENT || this == PAID || this == COMPLETED;
    }

    public Set<OrderStatus> allowedTargets() {
        return EnumSet.copyOf(ALLOWED.get(this));
    }
}
