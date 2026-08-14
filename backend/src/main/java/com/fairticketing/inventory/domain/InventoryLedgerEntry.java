package com.fairticketing.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only history of every stock movement. Once Redis holds the hot counter
 * this is what the reconciliation job replays to decide which side drifted.
 */
@Entity
@Table(name = "inventory_ledger")
@Getter
@NoArgsConstructor
public class InventoryLedgerEntry {

    public enum Reason {
        RESERVE,
        RELEASE_CANCELLED,
        RELEASE_EXPIRED,
        RECONCILIATION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tier_id", nullable = false)
    private Long tierId;

    @Column(name = "order_id")
    private Long orderId;

    /** Positive when stock is taken, negative when it is handed back. */
    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Reason reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public InventoryLedgerEntry(Long tierId, Long orderId, int delta, Reason reason, Instant createdAt) {
        this.tierId = tierId;
        this.orderId = orderId;
        this.delta = delta;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
