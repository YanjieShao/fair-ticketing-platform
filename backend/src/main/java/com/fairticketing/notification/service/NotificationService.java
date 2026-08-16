package com.fairticketing.notification.service;

import com.fairticketing.notification.domain.Notification;
import com.fairticketing.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Writes the messages the product has to send. Transactional copy stays on
 * templates. Insight copy is a consumer of analytics numbers, not a second
 * notification platform. Duplicates are swallowed because the unique key is
 * the real guarantee that nobody is told twice.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUser(Long userId,
                           String type,
                           String title,
                           String body,
                           String dedupeKey) {
        write(userId, type, "INFO", title, body, null, "ORDER_EVENT", "TEMPLATE", dedupeKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyInsight(Long userId,
                              String title,
                              String body,
                              String payloadJson,
                              String generatedBy,
                              String dedupeKey) {
        write(userId, "SALES_INSIGHT", "INFO", title, body, payloadJson, "ANALYTICS", generatedBy, dedupeKey);
    }

    private void write(Long userId,
                       String type,
                       String severity,
                       String title,
                       String body,
                       String payloadJson,
                       String sourceType,
                       String generatedBy,
                       String dedupeKey) {
        if (notifications.existsByDedupeKey(dedupeKey)) {
            return;
        }
        try {
            notifications.save(new Notification(
                    userId,
                    type,
                    severity,
                    title,
                    body,
                    payloadJson,
                    sourceType,
                    generatedBy,
                    dedupeKey,
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Skipped duplicate notification {}", dedupeKey);
        }
    }
}
