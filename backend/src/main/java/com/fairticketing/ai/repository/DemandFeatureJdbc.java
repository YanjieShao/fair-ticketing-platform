package com.fairticketing.ai.repository;

import com.fairticketing.ai.service.DemandForecastPort.EventFeatures;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class DemandFeatureJdbc {

    private final JdbcTemplate jdbc;

    public DemandFeatureJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EventFeatures> closedHistory() {
        return load("""
                where e.status = 'CLOSED'
                """);
    }

    public List<EventFeatures> upcoming() {
        return load("""
                where e.status in ('ON_SALE', 'DRAFT', 'SOLD_OUT')
                """);
    }

    private List<EventFeatures> load(String statusClause) {
        return jdbc.query("""
                        select e.id,
                               a.popularity_score,
                               v.capacity,
                               v.city,
                               a.genre,
                               e.category,
                               e.starts_at,
                               e.sales_start_at,
                               avg(t.price_cents) as avg_price_cents,
                               coalesce(sum(t.reserved_quantity), 0) as sold_quantity
                          from events e
                          join artists a on a.id = e.artist_id
                          join venues v on v.id = e.venue_id
                          join ticket_tiers t on t.event_id = e.id
                        """ + statusClause + """
                         group by e.id, a.popularity_score, v.capacity, v.city, a.genre,
                                  e.category, e.starts_at, e.sales_start_at
                        """,
                (rs, rowNum) -> {
                    Instant startsAt = rs.getTimestamp("starts_at").toInstant();
                    Instant salesStartAt = rs.getTimestamp("sales_start_at").toInstant();
                    int leadTimeDays = (int) Math.max(0, Duration.between(salesStartAt, startsAt).toDays());
                    DayOfWeek day = startsAt.atZone(ZoneOffset.UTC).getDayOfWeek();
                    boolean weekend = day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
                    return new EventFeatures(
                            rs.getLong("id"),
                            rs.getInt("popularity_score"),
                            rs.getInt("capacity"),
                            rs.getInt("avg_price_cents"),
                            weekend,
                            leadTimeDays,
                            rs.getString("genre"),
                            rs.getString("city"),
                            rs.getString("category"),
                            rs.getInt("sold_quantity"));
                });
    }
}
