package com.fairticketing.event.web;

import com.fairticketing.event.service.EventQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final int MAX_PAGE_SIZE = 50;

    private final EventQueryService events;

    public EventController(EventQueryService events) {
        this.events = events;
    }

    @GetMapping
    public Page<EventSummaryResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer minPriceCents,
            @RequestParam(required = false) Integer maxPriceCents,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        EventSearchCriteria criteria = new EventSearchCriteria(
                city, artist, category, from, to, minPriceCents, maxPriceCents);

        return events.search(criteria, PageRequest.of(
                Math.max(0, page),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by("startsAt").ascending()));
    }

    @GetMapping("/{eventId}")
    public EventDetailResponse detail(@PathVariable Long eventId) {
        return events.detail(eventId);
    }
}
