package com.fairticketing.analytics;

import java.time.Duration;
import java.time.Instant;

/**
 * Numbers only. Copywriters, including the LLM, are not allowed to invent
 * anything that is not on this record.
 */
public record SalesSnapshot(
        long eventId,
        String title,
        String artistName,
        String status,
        long hoursOnSale,
        int capacity,
        int reserved,
        int remaining,
        int soldPercent,
        int waitlistPeople,
        int waitlistTickets,
        Integer waitlistVsRemainingPercent,
        Integer expectedDemand,
        String demandRisk) {

    public static SalesSnapshot from(long eventId,
                                     String title,
                                     String artistName,
                                     String status,
                                     Instant salesStartAt,
                                     Instant now,
                                     int capacity,
                                     int reserved,
                                     int waitlistPeople,
                                     int waitlistTickets,
                                     Integer expectedDemand,
                                     String demandRisk) {
        int house = Math.max(capacity, 0);
        int held = Math.max(reserved, 0);
        int remaining = Math.max(house - held, 0);
        int soldPercent = house == 0 ? 0 : (int) Math.round(held * 100.0 / house);
        Integer waitlistVsRemaining = remaining == 0
                ? null
                : (int) Math.round(waitlistTickets * 100.0 / remaining);
        long hours = salesStartAt == null || now.isBefore(salesStartAt)
                ? 0
                : Math.max(0, Duration.between(salesStartAt, now).toHours());
        return new SalesSnapshot(
                eventId,
                title,
                artistName,
                status,
                hours,
                house,
                held,
                remaining,
                soldPercent,
                waitlistPeople,
                waitlistTickets,
                waitlistVsRemaining,
                expectedDemand,
                demandRisk);
    }
}
