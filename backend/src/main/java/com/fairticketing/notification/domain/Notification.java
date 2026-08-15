package com.fairticketing.notification.domain;

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
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "generated_by", nullable = false, length = 20)
    private String generatedBy;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 120)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Notification(Long userId,
                        String type,
                        String severity,
                        String title,
                        String body,
                        String payloadJson,
                        String sourceType,
                        String generatedBy,
                        String dedupeKey,
                        Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.body = body;
        this.payloadJson = payloadJson;
        this.sourceType = sourceType;
        this.generatedBy = generatedBy;
        this.dedupeKey = dedupeKey;
        this.createdAt = createdAt;
    }
}
