package com.fairticketing.inventory.repository;

import com.fairticketing.event.domain.EventStatus;

import java.time.Instant;

/**
 * Everything checkout needs to validate a purchase, read in one query.
 *
 * <p>It is a projection rather than the entity on purpose: loading the tier
 * entity here would put a copy in the persistence context whose version is
 * immediately stale once a competing buyer commits.
 */
public record TierPurchaseView(
        Long tierId,
        Long eventId,
        int priceCents,
        int maxPerUser,
        EventStatus eventStatus,
        Instant salesStartAt,
        Instant salesEndAt,
        boolean waitingRoomEnabled) {
}
