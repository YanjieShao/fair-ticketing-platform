package com.fairticketing.ai.web;

import com.fairticketing.event.domain.EventStatus;

import java.util.List;

public record RecommendationResponse(
        Long id,
        String title,
        String artistName,
        String genre,
        String city,
        EventStatus status,
        int ticketsAvailable,
        int lowestPriceCents,
        int score,
        List<String> reasons) {
}
