package com.fairticketing.notification.web;

import com.fairticketing.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public Page<NotificationResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return notifications
                .findByUserIdOrderByCreatedAtDesc(
                        Long.valueOf(jwt.getSubject()),
                        PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 50)))
                .map(NotificationResponse::from);
    }
}
