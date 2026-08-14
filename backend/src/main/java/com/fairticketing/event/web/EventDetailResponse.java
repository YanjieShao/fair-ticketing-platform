package com.fairticketing.event.web;

import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.TicketTier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EventDetailResponse(
        Long id,
        String title,
        String artistName,
        String genre,
        String venueName,
        String city,
        String country,
        String timezone,
        String category,
        EventStatus status,
        Instant startsAt,
        Instant salesStartAt,
        Instant salesEndAt,
        boolean waitingRoomEnabled,
        ForecastView forecast,
        List<TierView> tiers) {

    public record ForecastView(
            int expectedDemand,
            int capacity,
            double demandRatio,
            String riskLevel,
            String modelVersion) {
    }

    public record TierView(
            Long id,
            String name,
            int priceCents,
            String currency,
            int totalQuantity,
            int availableQuantity,
            int maxPerUser,
            boolean soldOut) {

        static TierView from(TicketTier tier, int remaining) {
            return new TierView(
                    tier.getId(),
                    tier.getName(),
                    tier.getPriceCents(),
                    tier.getCurrency(),
                    tier.getTotalQuantity(),
                    remaining,
                    tier.getMaxPerUser(),
                    remaining <= 0);
        }
    }

    public static EventDetailResponse from(Event event,
                                           List<TicketTier> tiers,
                                           Map<Long, Integer> remaining,
                                           ForecastView forecast,
                                           boolean waitingRoomEnabled) {
        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getArtist().getName(),
                event.getArtist().getGenre(),
                event.getVenue().getName(),
                event.getVenue().getCity(),
                event.getVenue().getCountry(),
                event.getVenue().getTimezone(),
                event.getCategory(),
                event.getStatus(),
                event.getStartsAt(),
                event.getSalesStartAt(),
                event.getSalesEndAt(),
                waitingRoomEnabled,
                forecast,
                tiers.stream()
                        .map(tier -> TierView.from(tier, remaining.getOrDefault(tier.getId(), tier.availableQuantity())))
                        .toList());
    }
}
