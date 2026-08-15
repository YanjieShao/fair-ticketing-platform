package com.fairticketing.ai.web;

import com.fairticketing.ai.domain.AiInsight;

import java.time.Instant;

public record InsightResponse(
        Long id,
        Long eventId,
        String content,
        String generatedBy,
        Instant createdAt,
        String payloadJson) {

    public static InsightResponse from(AiInsight insight) {
        return new InsightResponse(
                insight.getId(),
                insight.getScopeId(),
                insight.getContent(),
                insight.getGeneratedBy(),
                insight.getCreatedAt(),
                insight.getPayloadJson());
    }
}
