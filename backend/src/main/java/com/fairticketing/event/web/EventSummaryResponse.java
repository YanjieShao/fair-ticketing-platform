package com.fairticketing.event.web;

import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.service.EventQueryService.Availability;

import java.time.Instant;

public record EventSummaryResponse(
        Long id,
        String title,
        String artistName,
        String genre,
        String venueName,
        String city,
        String country,
        String category,
        EventStatus status,
        Instant startsAt,
        Instant salesStartAt,
        boolean waitingRoomEnabled,
        int ticketsAvailable,
        int lowestPriceCents) {

    public static EventSummaryResponse from(Event event, Availability availability) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getArtist().getName(),
                event.getArtist().getGenre(),
                event.getVenue().getName(),
                event.getVenue().getCity(),
                event.getVenue().getCountry(),
                event.getCategory(),
                event.getStatus(),
                event.getStartsAt(),
                event.getSalesStartAt(),
                event.isWaitingRoomEnabled(),
                availability.ticketsAvailable(),
                availability.lowestPriceCents());
    }
}
