package com.fairticketing.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Foreign keys are held as plain identifiers rather than associations: checkout
 * is the hottest path in the system and has no reason to hydrate the event,
 * tier and user graphs on every write.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class TicketOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "tier_id", nullable = false)
    private Long tierId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private int unitPriceCents;

    @Column(name = "total_cents", nullable = false)
    private int totalCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    /**
     * Set while the order occupies inventory and cleared once it does not.
     * A unique index on this column is what stops a user from holding two
     * live orders for the same event; MySQL ignores NULLs in unique indexes.
     */
    @Column(name = "active_lock_key", unique = true, length = 64)
    private String activeLockKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;

    public static TicketOrder create(String orderNo,
                                     Long userId,
                                     Long eventId,
                                     Long tierId,
                                     int quantity,
                                     int unitPriceCents,
                                     String idempotencyKey,
                                     Instant createdAt,
                                     Instant expiresAt) {
        TicketOrder order = new TicketOrder();
        order.orderNo = orderNo;
        order.userId = userId;
        order.eventId = eventId;
        order.tierId = tierId;
        order.quantity = quantity;
        order.unitPriceCents = unitPriceCents;
        order.totalCents = unitPriceCents * quantity;
        order.status = OrderStatus.CREATED;
        order.idempotencyKey = idempotencyKey;
        order.createdAt = createdAt;
        order.expiresAt = expiresAt;
        order.activeLockKey = lockKey(userId, eventId);
        return order;
    }

    public static String lockKey(Long userId, Long eventId) {
        return userId + ":" + eventId;
    }

    /**
     * Moves the order and keeps the derived fields honest: the inventory lock is
     * released exactly when the new state stops occupying inventory.
     */
    public void transitionTo(OrderStatus target, Instant at) {
        this.status = this.status.transitionTo(target);
        this.activeLockKey = this.status.occupiesInventory() ? lockKey(userId, eventId) : null;

        switch (this.status) {
            case PAID -> this.paidAt = at;
            case COMPLETED -> this.completedAt = at;
            case EXPIRED, CANCELLED -> this.closedAt = at;
            default -> {
            }
        }
    }

    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !status.isTerminal() && now.isAfter(expiresAt);
    }
}
