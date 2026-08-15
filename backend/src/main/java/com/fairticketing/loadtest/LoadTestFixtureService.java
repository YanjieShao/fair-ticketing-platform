package com.fairticketing.loadtest;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.service.TokenService;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.event.repository.ArtistRepository;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.repository.VenueRepository;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.order.repository.TicketOrderRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a disposable on-sale for the HTTP stampede. Emails are
 * {@code load-{n}@lt.local} so a rerun can wipe them without touching seed data.
 */
@Service
@ConditionalOnProperty(prefix = "ticketing.load-test", name = "enabled", havingValue = "true")
public class LoadTestFixtureService {

    static final String CATEGORY = "LOADTEST";
    static final String ARTIST = "Load Test Act";
    static final String VENUE = "Load Test Arena";
    static final String EMAIL_PREFIX = "load-";
    static final String EMAIL_SUFFIX = "@lt.local";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final ArtistRepository artists;
    private final VenueRepository venues;
    private final EventRepository events;
    private final TicketTierRepository tiers;
    private final TicketOrderRepository orders;
    private final InventoryService inventory;
    private final Clock clock;

    public LoadTestFixtureService(JdbcTemplate jdbc,
                                  PasswordEncoder passwords,
                                  TokenService tokens,
                                  ArtistRepository artists,
                                  VenueRepository venues,
                                  EventRepository events,
                                  TicketTierRepository tiers,
                                  TicketOrderRepository orders,
                                  InventoryService inventory,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
        this.artists = artists;
        this.venues = venues;
        this.events = events;
        this.tiers = tiers;
        this.orders = orders;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Transactional
    public Fixture create(int buyers, int stock) {
        wipe();

        Instant now = Instant.now(clock);
        Artist artist = artists.save(new Artist(ARTIST, "Pop", 50));
        Venue venue = venues.save(new Venue(VENUE, "Dublin", "Ireland", Math.max(stock, 1), "Europe/Dublin"));

        Event event = new Event();
        event.setArtist(artist);
        event.setVenue(venue);
        event.setTitle("Load test on-sale");
        event.setCategory(CATEGORY);
        event.setStatus(EventStatus.ON_SALE);
        event.setStartsAt(now.plus(Duration.ofDays(60)));
        event.setSalesStartAt(now.minus(Duration.ofHours(1)));
        event.setSalesEndAt(now.plus(Duration.ofDays(59)));
        event.setWaitingRoomEnabled(false);
        event.setCreatedAt(now);
        events.save(event);

        TicketTier tier = new TicketTier();
        tier.setEvent(event);
        tier.setName("Standing");
        tier.setPriceCents(5_000);
        tier.setCurrency("EUR");
        tier.setTotalQuantity(stock);
        tier.setReservedQuantity(0);
        tier.setMaxPerUser(4);
        Long tierId = tiers.save(tier).getId();

        String hash = passwords.encode("password123");
        Timestamp createdAt = Timestamp.from(now);
        jdbc.batchUpdate("""
                        insert into users (email, password_hash, display_name, role, created_at)
                        values (?, ?, ?, ?, ?)
                        """,
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setString(1, EMAIL_PREFIX + i + EMAIL_SUFFIX);
                        ps.setString(2, hash);
                        ps.setString(3, "Load " + i);
                        ps.setString(4, UserRole.USER.name());
                        ps.setTimestamp(5, createdAt);
                    }

                    @Override
                    public int getBatchSize() {
                        return buyers;
                    }
                });

        List<Long> ids = jdbc.queryForList(
                "select id from users where email like ? order by id",
                Long.class,
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        List<String> issued = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            UserAccount user = new UserAccount();
            user.setId(ids.get(i));
            user.setEmail(EMAIL_PREFIX + i + EMAIL_SUFFIX);
            user.setRole(UserRole.USER);
            issued.add(tokens.issue(user).value());
        }

        return new Fixture(event.getId(), tierId, stock, buyers, issued);
    }

    @Transactional(readOnly = true)
    public Result result(Long tierId) {
        TicketTier tier = tiers.findById(tierId).orElseThrow();
        int remaining = inventory.remaining(tier);
        long orderCount = orders.countByTierId(tierId);
        return new Result(
                tier.getId(),
                tier.getTotalQuantity(),
                tier.getReservedQuantity(),
                remaining,
                orderCount,
                orderCount > tier.getTotalQuantity() || remaining < 0);
    }

    private void wipe() {
        jdbc.update("""
                delete p from payments p
                join orders o on o.id = p.order_id
                join events e on e.id = o.event_id
                where e.category = ?
                """, CATEGORY);
        jdbc.update("""
                delete w from waitlist_entries w
                join events e on e.id = w.event_id
                where e.category = ?
                """, CATEGORY);
        jdbc.update("""
                delete l from inventory_ledger l
                join ticket_tiers t on t.id = l.tier_id
                join events e on e.id = t.event_id
                where e.category = ?
                """, CATEGORY);
        jdbc.update("""
                delete o from orders o
                join events e on e.id = o.event_id
                where e.category = ?
                """, CATEGORY);
        jdbc.update("""
                delete f from demand_forecasts f
                join events e on e.id = f.event_id
                where e.category = ?
                """, CATEGORY);
        jdbc.update("delete from ai_insights where scope_type = 'EVENT' and scope_id in (select id from events where category = ?)",
                CATEGORY);
        jdbc.update("delete from ticket_tiers where event_id in (select id from events where category = ?)", CATEGORY);
        jdbc.update("delete from events where category = ?", CATEGORY);
        jdbc.update("delete from artists where name = ?", ARTIST);
        jdbc.update("delete from venues where name = ?", VENUE);
        jdbc.update("delete n from notifications n join users u on u.id = n.user_id where u.email like ?",
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
        jdbc.update("delete from users where email like ?", EMAIL_PREFIX + "%" + EMAIL_SUFFIX);
    }

    public record Fixture(long eventId, long tierId, int stock, int buyers, List<String> tokens) {
    }

    public record Result(long tierId, int total, int reserved, int remaining, long orderCount, boolean oversold) {
    }
}
