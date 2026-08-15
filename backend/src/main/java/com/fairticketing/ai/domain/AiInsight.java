package com.fairticketing.ai.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "ai_insights")
@Getter
@NoArgsConstructor
public class AiInsight {

    public static final String SCOPE_EVENT = "EVENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_type", nullable = false, length = 30)
    private String scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "generated_by", nullable = false, length = 20)
    private String generatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AiInsight(String scopeType,
                     Long scopeId,
                     String content,
                     String payloadJson,
                     String generatedBy,
                     Instant createdAt) {
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.content = content;
        this.payloadJson = payloadJson;
        this.generatedBy = generatedBy;
        this.createdAt = createdAt;
    }
}
