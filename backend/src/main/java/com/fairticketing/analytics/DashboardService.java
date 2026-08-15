package com.fairticketing.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DashboardService {

    private final SalesSnapshotRepository snapshots;
    private final DashboardRepository dashboard;
    private final Clock clock;

    public DashboardService(SalesSnapshotRepository snapshots, DashboardRepository dashboard, Clock clock) {
        this.snapshots = snapshots;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse load() {
        List<SalesSnapshot> live = snapshots.liveEvents();
        List<DashboardResponse.NamedCount> orderStatus =
                DashboardAssembler.complete(DashboardAssembler.ORDER_STATUSES, dashboard.orderStatus());
        List<DashboardResponse.NamedCount> forecastRisk =
                DashboardAssembler.complete(DashboardAssembler.RISK_LEVELS, dashboard.forecastRisk());

        LocalDate today = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        LocalDate from = today.minusDays(DashboardAssembler.TREND_DAYS - 1L);
        Instant trendStart = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<DashboardResponse.DailySales> trend =
                DashboardAssembler.padTrend(from, today, dashboard.paidSalesSince(trendStart));

        return new DashboardResponse(
                DashboardAssembler.kpis(live, DashboardAssembler.PaidSales.from(orderStatus)),
                orderStatus,
                forecastRisk,
                dashboard.categories(),
                trend,
                DashboardAssembler.hot(live));
    }
}
