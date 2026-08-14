package com.fairticketing.waitlist.web;

import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.domain.WaitlistStatus;

import java.time.Instant;

/**
 * @param peopleAhead 0 once an offer is in hand
 */
public record WaitlistResponse(
        Long id,
        Long eventId,
        Long tierId,
        WaitlistStatus status,
        int requestedQuantity,
        long positionSeq,
        long peopleAhead,
        Instant createdAt,
        Instant offeredAt,
        Instant offerExpiresAt,
        Long convertedOrderId) {

    public static WaitlistResponse from(WaitlistEntry entry, long peopleAhead) {
        return new WaitlistResponse(
                entry.getId(),
                entry.getEventId(),
                entry.getTierId(),
                entry.getStatus(),
                entry.getRequestedQuantity(),
                entry.getPositionSeq(),
                peopleAhead,
                entry.getCreatedAt(),
                entry.getOfferedAt(),
                entry.getOfferExpiresAt(),
                entry.getConvertedOrderId());
    }
}
