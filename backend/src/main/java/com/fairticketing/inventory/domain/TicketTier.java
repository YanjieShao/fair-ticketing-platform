package com.fairticketing.inventory.domain;

import com.fairticketing.event.domain.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ticket_tiers")
@Getter
@Setter
@NoArgsConstructor
public class TicketTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    /** Held by orders that still occupy inventory, paid or not. */
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "max_per_user", nullable = false)
    private int maxPerUser;

    @Version
    private long version;

    public int availableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public boolean isSoldOut() {
        return availableQuantity() <= 0;
    }
}
