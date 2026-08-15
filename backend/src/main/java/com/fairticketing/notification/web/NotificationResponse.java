package com.fairticketing.notification.web;

import com.fairticketing.notification.domain.Notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String body,
        String sourceType,
        String generatedBy,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getSourceType(),
                notification.getGeneratedBy(),
                notification.getCreatedAt());
    }
}
