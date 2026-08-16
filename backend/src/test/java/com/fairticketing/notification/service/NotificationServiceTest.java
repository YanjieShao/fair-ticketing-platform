package com.fairticketing.notification.service;

import com.fairticketing.notification.domain.Notification;
import com.fairticketing.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:27:00Z");

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a second insight in the same hour is skipped instead of hitting the unique key")
    void skips_duplicate_dedupe_key() {
        when(notifications.existsByDedupeKey("INSIGHT:123:2026081512:1")).thenReturn(true);

        service.notifyInsight(1L, "Sales insight", "copy", "{}", "TEMPLATE", "INSIGHT:123:2026081512:1");

        verify(notifications, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("a new insight is written once")
    void writes_new_insight() {
        when(notifications.existsByDedupeKey("INSIGHT:123:2026081512:1")).thenReturn(false);
        when(notifications.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));

        service.notifyInsight(1L, "Sales insight", "copy", "{}", "TEMPLATE", "INSIGHT:123:2026081512:1");

        verify(notifications).save(any(Notification.class));
    }
}
