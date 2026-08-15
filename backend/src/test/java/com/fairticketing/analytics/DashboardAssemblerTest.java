package com.fairticketing.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAssemblerTest {

    private static final Instant START = Instant.parse("2026-08-14T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    @DisplayName("sell-through and waitlist KPIs are sums of live snapshots plus paid orders")
    void rolls_up_snapshots_and_paid_orders() {
        SalesSnapshot onSale = SalesSnapshot.from(
                1L, "Night at Croke Park", "Taylor Swift", "ON_SALE",
                START, NOW, 10_000, 9_200, 200, 1_440, 125_000, "HIGH");
        SalesSnapshot soldOut = SalesSnapshot.from(
                2L, "Club show", "Act", "SOLD_OUT",
                START, NOW, 1_000, 1_000, 10, 40, null, null);

        DashboardResponse.Kpis kpis = DashboardAssembler.kpis(
                List.of(onSale, soldOut),
                new DashboardAssembler.PaidSales(80, 240, 1_200_000));

        assertThat(kpis.eventsOnSale()).isEqualTo(1);
        assertThat(kpis.eventsSoldOut()).isEqualTo(1);
        assertThat(kpis.capacity()).isEqualTo(11_000);
        assertThat(kpis.reserved()).isEqualTo(10_200);
        assertThat(kpis.remaining()).isEqualTo(800);
        assertThat(kpis.sellThroughPercent()).isEqualTo(93);
        assertThat(kpis.waitlistPeople()).isEqualTo(210);
        assertThat(kpis.waitlistTickets()).isEqualTo(1_480);
        assertThat(kpis.paidOrders()).isEqualTo(80);
        assertThat(kpis.paidCents()).isEqualTo(1_200_000);
    }

    @Test
    @DisplayName("hot list is the most sold-through live events, not a random sample")
    void hot_events_are_sorted_by_sold_percent() {
        SalesSnapshot quiet = snapshot(1, "Quiet", 20, 0);
        SalesSnapshot hot = snapshot(2, "Hot", 92, 200);
        SalesSnapshot mid = snapshot(3, "Mid", 50, 10);

        assertThat(DashboardAssembler.hot(List.of(quiet, hot, mid)))
                .extracting(DashboardResponse.HotEvent::title)
                .containsExactly("Hot", "Mid", "Quiet");
    }

    @Test
    @DisplayName("trend days with no paid orders stay on the chart as zeros")
    void pads_empty_trend_days() {
        List<DashboardResponse.DailySales> padded = DashboardAssembler.padTrend(
                LocalDate.parse("2026-08-13"),
                LocalDate.parse("2026-08-14"),
                List.of(new DashboardResponse.DailySales("2026-08-14", 3, 9, 45_000)));

        assertThat(padded).containsExactly(
                new DashboardResponse.DailySales("2026-08-13", 0, 0, 0),
                new DashboardResponse.DailySales("2026-08-14", 3, 9, 45_000));
    }

    @Test
    @DisplayName("order statuses the catalogue has not seen yet still appear as zero")
    void missing_order_statuses_are_zero() {
        List<DashboardResponse.NamedCount> complete = DashboardAssembler.complete(
                DashboardAssembler.ORDER_STATUSES,
                List.of(new DashboardResponse.NamedCount("PAID", 4, 12, 60_000)));

        assertThat(complete).hasSize(6);
        assertThat(complete.getFirst()).isEqualTo(new DashboardResponse.NamedCount("CREATED", 0, 0, 0));
        assertThat(DashboardAssembler.PaidSales.from(complete)).isEqualTo(new DashboardAssembler.PaidSales(4, 12, 60_000));
    }

    private static SalesSnapshot snapshot(long id, String title, int soldPercent, int waitlistTickets) {
        int capacity = 100;
        int reserved = soldPercent;
        return SalesSnapshot.from(
                id, title, "Act", "ON_SALE", START, NOW, capacity, reserved, 0, waitlistTickets, null, "MEDIUM");
    }
}
