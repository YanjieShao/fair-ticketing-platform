package com.fairticketing.event.web;

import java.time.Instant;

public record EventSearchCriteria(
        String city,
        String artist,
        String category,
        Instant from,
        Instant to,
        Integer minPriceCents,
        Integer maxPriceCents) {
}
