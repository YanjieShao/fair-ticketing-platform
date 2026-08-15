package com.fairticketing.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.util.List;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DashboardResponse.NamedCount> orderStatus() {
        return jdbc.query("""
                        select status as name,
                               count(*) as count,
                               coalesce(sum(quantity), 0) as tickets,
                               coalesce(sum(total_cents), 0) as cents
                          from orders
                         group by status
                        """,
                (rs, rowNum) -> new DashboardResponse.NamedCount(
                        rs.getString("name"),
                        rs.getLong("count"),
                        rs.getLong("tickets"),
                        rs.getLong("cents")));
    }

    public List<DashboardResponse.NamedCount> forecastRisk() {
        return jdbc.query("""
                        select df.risk_level as name,
                               count(*) as count,
                               0 as tickets,
                               0 as cents
                          from demand_forecasts df
                          join (
                                select event_id, max(generated_at) as generated_at
                                  from demand_forecasts
                                 group by event_id
                          ) latest on latest.event_id = df.event_id
                                  and latest.generated_at = df.generated_at
                         group by df.risk_level
                        """,
                (rs, rowNum) -> new DashboardResponse.NamedCount(
                        rs.getString("name"),
                        rs.getLong("count"),
                        0,
                        0));
    }

    public List<DashboardResponse.CategorySlice> categories() {
        return jdbc.query("""
                        select e.category,
                               coalesce(sum(t.reserved_quantity), 0) as reserved,
                               coalesce(sum(t.reserved_quantity * t.price_cents), 0) as held_cents
                          from events e
                          join ticket_tiers t on t.event_id = e.id
                         where e.status in ('ON_SALE', 'SOLD_OUT')
                         group by e.category
                         order by reserved desc
                        """,
                (rs, rowNum) -> new DashboardResponse.CategorySlice(
                        rs.getString("category"),
                        rs.getInt("reserved"),
                        rs.getLong("held_cents")));
    }

    public List<DashboardResponse.DailySales> paidSalesSince(Instant from) {
        return jdbc.query("""
                        select date(paid_at) as day,
                               count(*) as orders,
                               coalesce(sum(quantity), 0) as tickets,
                               coalesce(sum(total_cents), 0) as cents
                          from orders
                         where status in ('PAID', 'COMPLETED')
                           and paid_at >= ?
                         group by date(paid_at)
                         order by day
                        """,
                (rs, rowNum) -> {
                    Date day = rs.getDate("day");
                    return new DashboardResponse.DailySales(
                            day == null ? "" : day.toLocalDate().toString(),
                            rs.getLong("orders"),
                            rs.getLong("tickets"),
                            rs.getLong("cents"));
                },
                java.sql.Timestamp.from(from));
    }
}
