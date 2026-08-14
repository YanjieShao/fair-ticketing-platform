package com.fairticketing.ai.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "demand_forecasts")
@Getter
@NoArgsConstructor
public class DemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "expected_demand", nullable = false)
    private int expectedDemand;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "demand_ratio", nullable = false, precision = 8, scale = 3)
    private BigDecimal demandRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private DemandRiskLevel riskLevel;

    @Column(name = "model_version", nullable = false, length = 40)
    private String modelVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public DemandForecast(Long eventId,
                          int expectedDemand,
                          int capacity,
                          BigDecimal demandRatio,
                          DemandRiskLevel riskLevel,
                          String modelVersion,
                          Instant generatedAt) {
        this.eventId = eventId;
        this.expectedDemand = expectedDemand;
        this.capacity = capacity;
        this.demandRatio = demandRatio;
        this.riskLevel = riskLevel;
        this.modelVersion = modelVersion;
        this.generatedAt = generatedAt;
    }
}
