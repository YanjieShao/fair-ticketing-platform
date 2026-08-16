package com.fairticketing.order.web;

import com.fairticketing.inventory.repository.WaitlistShowView;
import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.order.domain.TicketOrder;

import java.time.Instant;

public record OrderResponse(
        String orderNo,
        Long eventId,
        Long tierId,
        int quantity,
        int unitPriceCents,
        int totalCents,
        OrderStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant paidAt,
        Instant completedAt,
        String eventTitle,
        String artistName,
        String tierName,
        String venueName,
        String city,
        Instant startsAt,
        String venueTimezone) {

    public static OrderResponse from(TicketOrder order, WaitlistShowView show) {
        return new OrderResponse(
                order.getOrderNo(),
                order.getEventId(),
                order.getTierId(),
                order.getQuantity(),
                order.getUnitPriceCents(),
                order.getTotalCents(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getExpiresAt(),
                order.getPaidAt(),
                order.getCompletedAt(),
                show == null ? null : show.eventTitle(),
                show == null ? null : show.artistName(),
                show == null ? null : show.tierName(),
                show == null ? null : show.venueName(),
                show == null ? null : show.city(),
                show == null ? null : show.startsAt(),
                show == null || show.venueTimezone() == null || show.venueTimezone().isBlank()
                        ? "UTC"
                        : show.venueTimezone());
    }
}
