package com.fairticketing.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Repository
public class SalesSnapshotRepository {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SalesSnapshotRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<SalesSnapshot> liveEvents() {
        Instant now = Instant.now(clock);
        return jdbc.query("""
                        select e.id,
                               e.title,
                               a.name as artist_name,
                               e.status,
                               e.sales_start_at,
                               coalesce(sum(t.total_quantity), 0) as capacity,
                               coalesce(sum(t.reserved_quantity), 0) as reserved,
                               coalesce(w.people, 0) as waitlist_people,
                               coalesce(w.tickets, 0) as waitlist_tickets,
                               f.expected_demand,
                               f.risk_level
                          from events e
                          join artists a on a.id = e.artist_id
                          join ticket_tiers t on t.event_id = e.id
                          left join (
                                select event_id,
                                       count(*) as people,
                                       coalesce(sum(requested_quantity), 0) as tickets
                                  from waitlist_entries
                                 where status in ('WAITING', 'OFFERED')
                                 group by event_id
                          ) w on w.event_id = e.id
                          left join (
                                select df.event_id, df.expected_demand, df.risk_level
                                  from demand_forecasts df
                                  join (
                                        select event_id, max(generated_at) as generated_at
                                          from demand_forecasts
                                         group by event_id
                                  ) latest on latest.event_id = df.event_id
                                          and latest.generated_at = df.generated_at
                          ) f on f.event_id = e.id
                         where e.status in ('ON_SALE', 'SOLD_OUT')
                         group by e.id, e.title, a.name, e.status, e.sales_start_at,
                                  w.people, w.tickets, f.expected_demand, f.risk_level
                        """,
                (rs, rowNum) -> SalesSnapshot.from(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("artist_name"),
                        rs.getString("status"),
                        rs.getTimestamp("sales_start_at") == null
                                ? null
                                : rs.getTimestamp("sales_start_at").toInstant(),
                        now,
                        rs.getInt("capacity"),
                        rs.getInt("reserved"),
                        rs.getInt("waitlist_people"),
                        rs.getInt("waitlist_tickets"),
                        rs.getObject("expected_demand") == null ? null : rs.getInt("expected_demand"),
                        rs.getString("risk_level")));
    }
}
