package com.fairticketing.ai.service;

import com.fairticketing.ai.recommend.ContentSimilarity;
import com.fairticketing.ai.recommend.ContentSimilarity.EventProfile;
import com.fairticketing.ai.web.RecommendationResponse;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.service.EventQueryService.Availability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads on-sale catalogue and ranks it. Checkout never calls this.
 */
@Service
public class WaitlistRecommendationService {

    private final EventRepository events;

    public WaitlistRecommendationService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> forEvent(Long eventId) {
        Event source = events.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Event " + eventId + " not found"));

        List<Event> others = events.findOnSaleOtherThan(EventStatus.ON_SALE, eventId);
        Map<Long, Availability> availability = availabilityFor(source, others);

        EventProfile sourceProfile = profile(source, availability.getOrDefault(source.getId(), Availability.NONE));
        List<EventProfile> candidates = others.stream()
                .map(event -> profile(event, availability.getOrDefault(event.getId(), Availability.NONE)))
                .toList();

        Map<Long, Event> byId = new HashMap<>();
        for (Event event : others) {
            byId.put(event.getId(), event);
        }

        return ContentSimilarity.rank(sourceProfile, candidates).stream()
                .map(scored -> toResponse(scored, byId.get(scored.profile().eventId())))
                .toList();
    }

    private Map<Long, Availability> availabilityFor(Event source, List<Event> others) {
        List<Long> ids = new ArrayList<>();
        ids.add(source.getId());
        others.forEach(event -> ids.add(event.getId()));
        Map<Long, Availability> result = new HashMap<>();
        for (Object[] row : events.availabilityByEvent(ids)) {
            result.put((Long) row[0], new Availability(((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
        }
        return result;
    }

    private static EventProfile profile(Event event, Availability availability) {
        Long artistId = event.getArtist().getId();
        return new EventProfile(
                event.getId(),
                artistId == null ? 0L : artistId,
                event.getArtist().getName(),
                event.getTitle(),
                event.getArtist().getGenre(),
                event.getCategory(),
                event.getVenue().getCity(),
                availability.lowestPriceCents(),
                availability.ticketsAvailable());
    }

    private static RecommendationResponse toResponse(ContentSimilarity.Scored scored, Event event) {
        ContentSimilarity.EventProfile profile = scored.profile();
        return new RecommendationResponse(
                profile.eventId(),
                event.getTitle(),
                event.getArtist().getName(),
                event.getArtist().getGenre(),
                event.getVenue().getCity(),
                event.getStatus(),
                profile.ticketsAvailable(),
                profile.lowestPriceCents(),
                scored.score(),
                scored.reasons());
    }
}
