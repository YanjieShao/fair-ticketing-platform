package com.fairticketing.payment.domain;

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

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment {

    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "provider_ref", nullable = false, unique = true, length = 64)
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(Long orderId, String providerRef, Status status, int amountCents, Instant at) {
        this.orderId = orderId;
        this.providerRef = providerRef;
        this.status = status;
        this.amountCents = amountCents;
        this.createdAt = at;
        this.updatedAt = at;
    }
}
