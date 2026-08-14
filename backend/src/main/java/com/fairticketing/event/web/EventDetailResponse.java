package com.fairticketing.event.web;

import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.TicketTier;

import java.time.Instant;
import java.util.List;

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
        List<TierView> tiers) {

    public record TierView(
            Long id,
            String name,
            int priceCents,
            String currency,
            int totalQuantity,
            int availableQuantity,
            int maxPerUser,
            boolean soldOut) {

        static TierView from(TicketTier tier) {
            return new TierView(
                    tier.getId(),
                    tier.getName(),
                    tier.getPriceCents(),
                    tier.getCurrency(),
                    tier.getTotalQuantity(),
                    tier.availableQuantity(),
                    tier.getMaxPerUser(),
                    tier.isSoldOut());
        }
    }

    public static EventDetailResponse from(Event event, List<TicketTier> tiers) {
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
                event.isWaitingRoomEnabled(),
                tiers.stream().map(TierView::from).toList());
    }
}
