package com.fairticketing.notification.service;

import com.fairticketing.notification.domain.Notification;
import com.fairticketing.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Writes the transactional messages the product has to send. Insight-style
 * copy comes later; duplicates are swallowed because the unique key is the
 * real guarantee that a buyer is not told twice.
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

    public void notifyUser(Long userId,
                           String type,
                           String title,
                           String body,
                           String dedupeKey) {
        try {
            notifications.save(new Notification(
                    userId,
                    type,
                    "INFO",
                    title,
                    body,
                    "ORDER_EVENT",
                    "TEMPLATE",
                    dedupeKey,
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Skipped duplicate notification {}", dedupeKey);
        }
    }
}
