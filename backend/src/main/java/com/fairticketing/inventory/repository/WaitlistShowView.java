package com.fairticketing.inventory.repository;

import java.time.Instant;

/**
 * Labels waitlist and order cards so two shows are distinguishable.
 */
public record WaitlistShowView(
        Long tierId,
        String tierName,
        Long eventId,
        String eventTitle,
        String artistName,
        String venueName,
        String city,
        Instant startsAt,
        String venueTimezone) {
}
