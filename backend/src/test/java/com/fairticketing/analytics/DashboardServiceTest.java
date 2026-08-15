package com.fairticketing.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    @DisplayName("assembles KPIs, padded trend, and hot events without calling checkout")
    void assembles_dashboard_from_snapshots_and_aggregates() {
        SalesSnapshotRepository snapshots = mock(SalesSnapshotRepository.class);
        DashboardRepository dashboard = mock(DashboardRepository.class);
        DashboardService service = new DashboardService(
                snapshots, dashboard, Clock.fixed(NOW, ZoneOffset.UTC));

        SalesSnapshot hot = SalesSnapshot.from(
                1L, "Night at Croke Park", "Taylor Swift", "ON_SALE",
                NOW.minusSeconds(3 * 3600), NOW, 10_000, 9_200, 200, 1_440, 125_000, "HIGH");
        when(snapshots.liveEvents()).thenReturn(List.of(hot));
        when(dashboard.orderStatus()).thenReturn(List.of(
                new DashboardResponse.NamedCount("PAID", 10, 30, 150_000),
                new DashboardResponse.NamedCount("COMPLETED", 5, 15, 75_000)));
        when(dashboard.forecastRisk()).thenReturn(List.of(
                new DashboardResponse.NamedCount("HIGH", 1, 0, 0)));
        when(dashboard.categories()).thenReturn(List.of(
                new DashboardResponse.CategorySlice("Concert", 9_200, 73_600_000)));
        when(dashboard.paidSalesSince(any())).thenReturn(List.of(
                new DashboardResponse.DailySales("2026-08-14", 4, 12, 60_000)));

        DashboardResponse response = service.load();

        assertThat(response.kpis().sellThroughPercent()).isEqualTo(92);
        assertThat(response.kpis().paidOrders()).isEqualTo(15);
        assertThat(response.kpis().paidCents()).isEqualTo(225_000);
        assertThat(response.orderStatus()).hasSize(6);
        assertThat(response.forecastRisk()).extracting(DashboardResponse.NamedCount::name)
                .containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(response.salesTrend()).hasSize(14);
        assertThat(response.salesTrend().getLast().day()).isEqualTo("2026-08-14");
        assertThat(response.salesTrend().getLast().orders()).isEqualTo(4);
        assertThat(response.hotEvents()).extracting(DashboardResponse.HotEvent::title)
                .containsExactly("Night at Croke Park");
    }
}
