package com.fairticketing.event.web;

import com.fairticketing.event.service.EventAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
public class EventAdminController {

    private final EventAdminService events;

    public EventAdminController(EventAdminService events) {
        this.events = events;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDetailResponse create(@Valid @RequestBody CreateEventRequest request) {
        return events.create(request);
    }

    @PostMapping("/{eventId}/publish")
    public EventDetailResponse publish(@PathVariable Long eventId) {
        return events.publish(eventId);
    }

    @PostMapping("/{eventId}/cancel")
    public EventDetailResponse cancel(@PathVariable Long eventId) {
        return events.cancelUnsold(eventId);
    }
}
