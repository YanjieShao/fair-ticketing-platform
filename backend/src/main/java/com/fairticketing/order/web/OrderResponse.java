package com.fairticketing.order.web;

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
        Instant completedAt) {

    public static OrderResponse from(TicketOrder order) {
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
                order.getCompletedAt());
    }
}
