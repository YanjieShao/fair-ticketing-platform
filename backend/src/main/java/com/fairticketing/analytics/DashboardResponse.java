package com.fairticketing.analytics;

import java.util.List;

public record DashboardResponse(
        Kpis kpis,
        List<NamedCount> orderStatus,
        List<NamedCount> forecastRisk,
        List<CategorySlice> categories,
        List<DailySales> salesTrend,
        List<HotEvent> hotEvents) {

    public record Kpis(
            int eventsOnSale,
            int eventsSoldOut,
            int capacity,
            int reserved,
            int remaining,
            int sellThroughPercent,
            int waitlistPeople,
            int waitlistTickets,
            long paidOrders,
            long paidTickets,
            long paidCents) {
    }

    public record NamedCount(String name, long count, long tickets, long cents) {
    }

    public record CategorySlice(String category, int reserved, long heldCents) {
    }

    public record DailySales(String day, long orders, long tickets, long cents) {
    }

    public record HotEvent(
            long eventId,
            String title,
            String artistName,
            String status,
            int soldPercent,
            int reserved,
            int remaining,
            int waitlistPeople,
            int waitlistTickets,
            Integer waitlistVsRemainingPercent,
            String demandRisk) {
    }
}
