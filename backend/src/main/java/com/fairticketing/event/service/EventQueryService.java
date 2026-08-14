package com.fairticketing.event.service;

import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.web.EventDetailResponse;
import com.fairticketing.event.web.EventSearchCriteria;
import com.fairticketing.event.web.EventSummaryResponse;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventQueryService {

    private static final Set<EventStatus> BROWSABLE = Set.of(EventStatus.ON_SALE, EventStatus.SOLD_OUT);

    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final InventoryService inventory;

    public EventQueryService(EventRepository events, TicketTierRepository tiers, InventoryService inventory) {
        this.events = events;
        this.tiers = tiers;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryResponse> search(EventSearchCriteria criteria, Pageable pageable) {
        List<Specification<Event>> filters = new ArrayList<>();
        filters.add(EventSpecifications.statusIn(BROWSABLE));

        if (criteria.city() != null) {
            filters.add(EventSpecifications.inCity(criteria.city()));
        }
        if (criteria.artist() != null) {
            filters.add(EventSpecifications.artistNameContains(criteria.artist()));
        }
        if (criteria.category() != null) {
            filters.add(EventSpecifications.hasCategory(criteria.category()));
        }
        if (criteria.from() != null) {
            filters.add(EventSpecifications.startsOnOrAfter(criteria.from()));
        }
        if (criteria.to() != null) {
            filters.add(EventSpecifications.startsOnOrBefore(criteria.to()));
        }
        if (criteria.minPriceCents() != null || criteria.maxPriceCents() != null) {
            filters.add(EventSpecifications.hasTierPricedBetween(criteria.minPriceCents(), criteria.maxPriceCents()));
        }

        Page<Event> page = events.findAll(Specification.allOf(filters), pageable);
        Map<Long, Availability> availability = availabilityFor(page.getContent());

        return page.map(event -> EventSummaryResponse.from(
                event, availability.getOrDefault(event.getId(), Availability.NONE)));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse detail(Long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Event " + eventId + " not found"));

        List<TicketTier> eventTiers = tiers.findByEventIdOrderByPriceCentsAsc(eventId);
        // Asked of the active strategy rather than read off the row: this is the
        // number a buyer decides on, so it has to be the one checkout will use.
        Map<Long, Integer> remaining = eventTiers.stream()
                .collect(Collectors.toMap(TicketTier::getId, inventory::remaining));

        return EventDetailResponse.from(event, eventTiers, remaining);
    }

    private Map<Long, Availability> availabilityFor(List<Event> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = page.stream().map(Event::getId).toList();
        Map<Long, Availability> result = new HashMap<>();
        for (Object[] row : events.availabilityByEvent(ids)) {
            result.put((Long) row[0], new Availability(((Number) row[1]).intValue(), ((Number) row[2]).intValue()));
        }
        return result;
    }

    public record Availability(int ticketsAvailable, int lowestPriceCents) {
        static final Availability NONE = new Availability(0, 0);
    }
}
