package com.fairticketing.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns already-computed rows into dashboard totals. Charts never invent
 * figures; they only plot what this class returns.
 */
public final class DashboardAssembler {

    public static final int HOT_LIMIT = 8;
    public static final int TREND_DAYS = 14;

    public static final List<String> ORDER_STATUSES = List.of(
            "CREATED", "PENDING_PAYMENT", "PAID", "COMPLETED", "EXPIRED", "CANCELLED");
    public static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");

    private DashboardAssembler() {
    }

    public static DashboardResponse.Kpis kpis(List<SalesSnapshot> live, PaidSales paid) {
        int onSale = 0;
        int soldOut = 0;
        int capacity = 0;
        int reserved = 0;
        int remaining = 0;
        int waitlistPeople = 0;
        int waitlistTickets = 0;
        for (SalesSnapshot snapshot : live) {
            if ("ON_SALE".equals(snapshot.status())) {
                onSale++;
            } else if ("SOLD_OUT".equals(snapshot.status())) {
                soldOut++;
            }
            capacity += snapshot.capacity();
            reserved += snapshot.reserved();
            remaining += snapshot.remaining();
            waitlistPeople += snapshot.waitlistPeople();
            waitlistTickets += snapshot.waitlistTickets();
        }
        int sellThrough = capacity == 0 ? 0 : (int) Math.round(reserved * 100.0 / capacity);
        return new DashboardResponse.Kpis(
                onSale,
                soldOut,
                capacity,
                reserved,
                remaining,
                sellThrough,
                waitlistPeople,
                waitlistTickets,
                paid.orders(),
                paid.tickets(),
                paid.cents());
    }

    public static List<DashboardResponse.HotEvent> hot(List<SalesSnapshot> live) {
        return live.stream()
                .sorted(Comparator.comparingInt(SalesSnapshot::soldPercent).reversed()
                        .thenComparing(Comparator.comparingInt(SalesSnapshot::waitlistTickets).reversed())
                        .thenComparingLong(SalesSnapshot::eventId))
                .limit(HOT_LIMIT)
                .map(snapshot -> new DashboardResponse.HotEvent(
                        snapshot.eventId(),
                        snapshot.title(),
                        snapshot.artistName(),
                        snapshot.status(),
                        snapshot.soldPercent(),
                        snapshot.reserved(),
                        snapshot.remaining(),
                        snapshot.waitlistPeople(),
                        snapshot.waitlistTickets(),
                        snapshot.waitlistVsRemainingPercent(),
                        snapshot.demandRisk()))
                .toList();
    }

    public static List<DashboardResponse.DailySales> padTrend(
            LocalDate from,
            LocalDate to,
            List<DashboardResponse.DailySales> rows) {
        Map<String, DashboardResponse.DailySales> byDay = rows.stream()
                .collect(Collectors.toMap(DashboardResponse.DailySales::day, row -> row, (left, right) -> left));
        List<DashboardResponse.DailySales> padded = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String key = day.toString();
            padded.add(byDay.getOrDefault(key, new DashboardResponse.DailySales(key, 0, 0, 0)));
        }
        return padded;
    }

    public static List<DashboardResponse.NamedCount> complete(List<String> expected, List<DashboardResponse.NamedCount> rows) {
        Map<String, DashboardResponse.NamedCount> found = rows.stream()
                .collect(Collectors.toMap(DashboardResponse.NamedCount::name, row -> row, (left, right) -> left));
        List<DashboardResponse.NamedCount> complete = new ArrayList<>();
        for (String name : expected) {
            complete.add(found.getOrDefault(name, new DashboardResponse.NamedCount(name, 0, 0, 0)));
        }
        return complete;
    }

    public record PaidSales(long orders, long tickets, long cents) {
        public static PaidSales from(List<DashboardResponse.NamedCount> orderStatus) {
            long orders = 0;
            long tickets = 0;
            long cents = 0;
            for (DashboardResponse.NamedCount row : orderStatus) {
                if ("PAID".equals(row.name()) || "COMPLETED".equals(row.name())) {
                    orders += row.count();
                    tickets += row.tickets();
                    cents += row.cents();
                }
            }
            return new PaidSales(orders, tickets, cents);
        }
    }
}
