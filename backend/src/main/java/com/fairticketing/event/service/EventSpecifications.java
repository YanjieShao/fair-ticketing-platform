package com.fairticketing.event.service;

import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.inventory.domain.TicketTier;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;

/**
 * Filters composed one at a time rather than a single query riddled with
 * "or :param is null", which Hibernate cannot type when the value is absent.
 */
public final class EventSpecifications {

    private EventSpecifications() {
    }

    public static Specification<Event> statusIn(Collection<EventStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Event> inCity(String city) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("venue").get("city")), city.toLowerCase());
    }

    public static Specification<Event> artistNameContains(String fragment) {
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("artist").get("name")), "%" + fragment.toLowerCase() + "%");
    }

    public static Specification<Event> hasCategory(String category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Event> startsOnOrAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    public static Specification<Event> startsOnOrBefore(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), to);
    }

    /** Matches when any tier of the event falls inside the price range. */
    public static Specification<Event> hasTierPricedBetween(Integer minCents, Integer maxCents) {
        return (root, query, cb) -> {
            Subquery<Long> tiers = query.subquery(Long.class);
            var tier = tiers.from(TicketTier.class);
            tiers.select(cb.literal(1L)).where(cb.equal(tier.get("event"), root));

            if (minCents != null) {
                tiers.where(tiers.getRestriction(), cb.greaterThanOrEqualTo(tier.get("priceCents"), minCents));
            }
            if (maxCents != null) {
                tiers.where(tiers.getRestriction(), cb.lessThanOrEqualTo(tier.get("priceCents"), maxCents));
            }
            return cb.exists(tiers);
        };
    }
}
