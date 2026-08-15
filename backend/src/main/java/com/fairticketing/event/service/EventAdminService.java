package com.fairticketing.event.service;

import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.event.repository.ArtistRepository;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.repository.VenueRepository;
import com.fairticketing.event.web.CreateEventRequest;
import com.fairticketing.event.web.EventDetailResponse;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Admin writes. Buyers never see drafts. Taking a show down is only allowed
 * before it goes on sale, so this never has to unwind paid orders.
 */
@Service
public class EventAdminService {

    private final ArtistRepository artists;
    private final VenueRepository venues;
    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final EventQueryService queries;
    private final Clock clock;

    public EventAdminService(ArtistRepository artists,
                             VenueRepository venues,
                             EventRepository events,
                             TicketTierRepository tiers,
                             EventQueryService queries,
                             Clock clock) {
        this.artists = artists;
        this.venues = venues;
        this.events = events;
        this.tiers = tiers;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional
    public EventDetailResponse create(CreateEventRequest request) {
        if (request.salesEndAt().isBefore(request.salesStartAt())
                || request.startsAt().isBefore(request.salesEndAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Sales must start before they end, and the show must start after sales close");
        }

        Instant now = Instant.now(clock);
        Artist artist = artists.findByNameIgnoreCase(request.artistName().trim())
                .orElseGet(() -> artists.save(new Artist(
                        request.artistName().trim(),
                        request.genre().trim(),
                        request.popularityScore())));
        Venue venue = venues.findByNameAndCityIgnoreCase(request.venueName().trim(), request.city().trim())
                .orElseGet(() -> venues.save(new Venue(
                        request.venueName().trim(),
                        request.city().trim(),
                        request.country().trim(),
                        request.capacity(),
                        request.timezone().trim())));

        Event event = new Event();
        event.setArtist(artist);
        event.setVenue(venue);
        event.setTitle(request.title().trim());
        event.setCategory(request.category().trim());
        event.setStartsAt(request.startsAt());
        event.setSalesStartAt(request.salesStartAt());
        event.setSalesEndAt(request.salesEndAt());
        event.setWaitingRoomEnabled(request.waitingRoomEnabled());
        event.setCreatedAt(now);
        boolean live = !now.isBefore(request.salesStartAt()) && !now.isAfter(request.salesEndAt());
        event.setStatus(live ? EventStatus.ON_SALE : EventStatus.DRAFT);
        Long eventId = events.save(event).getId();

        for (CreateEventRequest.TierRequest spec : request.tiers()) {
            TicketTier tier = new TicketTier();
            tier.setEvent(event);
            tier.setName(spec.name().trim());
            tier.setPriceCents(spec.priceCents());
            tier.setCurrency("EUR");
            tier.setTotalQuantity(spec.totalQuantity());
            tier.setReservedQuantity(0);
            tier.setMaxPerUser(spec.maxPerUser());
            tiers.save(tier);
        }

        return queries.detail(eventId);
    }

    @Transactional
    public EventDetailResponse publish(Long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Event " + eventId + " not found"));
        event.transitionTo(EventStatus.ON_SALE);
        return queries.detail(eventId);
    }

    @Transactional
    public EventDetailResponse cancelUnsold(Long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Event " + eventId + " not found"));
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only a show that has not gone on sale can be taken down");
        }
        event.transitionTo(EventStatus.CANCELLED);
        return queries.detail(eventId);
    }
}
