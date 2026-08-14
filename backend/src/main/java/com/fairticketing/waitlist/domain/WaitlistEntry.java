package com.fairticketing.waitlist.domain;

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

@Entity
@Table(name = "waitlist_entries")
@Getter
@NoArgsConstructor
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "tier_id", nullable = false)
    private Long tierId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitlistStatus status;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Column(name = "position_seq", nullable = false)
    private long positionSeq;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "offer_expires_at")
    private Instant offerExpiresAt;

    @Column(name = "converted_order_id")
    private Long convertedOrderId;

    /**
     * Set while the entry is WAITING or OFFERED. The unique index on this
     * column is what stops a second live place in the same tier.
     */
    @Column(name = "active_lock_key", unique = true, length = 64)
    private String activeLockKey;

    @Version
    private long version;

    public static WaitlistEntry join(Long eventId,
                                     Long tierId,
                                     Long userId,
                                     int requestedQuantity,
                                     long positionSeq,
                                     Instant createdAt) {
        WaitlistEntry entry = new WaitlistEntry();
        entry.eventId = eventId;
        entry.tierId = tierId;
        entry.userId = userId;
        entry.status = WaitlistStatus.WAITING;
        entry.requestedQuantity = requestedQuantity;
        entry.positionSeq = positionSeq;
        entry.createdAt = createdAt;
        entry.activeLockKey = lockKey(userId, tierId);
        return entry;
    }

    public static String lockKey(Long userId, Long tierId) {
        return userId + ":" + tierId;
    }

    public void offer(Instant at, Instant expiresAt) {
        this.status = this.status.transitionTo(WaitlistStatus.OFFERED);
        this.offeredAt = at;
        this.offerExpiresAt = expiresAt;
    }

    public void convert(Long orderId) {
        this.status = this.status.transitionTo(WaitlistStatus.CONVERTED);
        this.convertedOrderId = orderId;
        this.activeLockKey = null;
    }

    public void expireOffer() {
        this.status = this.status.transitionTo(WaitlistStatus.OFFER_EXPIRED);
        this.activeLockKey = null;
    }

    public void cancel() {
        this.status = this.status.transitionTo(WaitlistStatus.CANCELLED);
        this.activeLockKey = null;
    }

    public boolean isOfferExpiredAt(Instant now) {
        return status == WaitlistStatus.OFFERED
                && offerExpiresAt != null
                && now.isAfter(offerExpiresAt);
    }
}
