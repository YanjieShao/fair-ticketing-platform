package com.fairticketing.seed;

import com.fairticketing.common.config.TicketingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds a sales history for a platform that has none. Without it the demand
 * forecasting model has nothing to learn from and the dashboards are empty.
 *
 * <p>Enable with {@code FT_SEED_ENABLED=true}. The random seed is fixed, so the
 * dataset is identical on every machine and results stay comparable.
 */
@Component
@ConditionalOnProperty(prefix = "ticketing.seed", name = "enabled", havingValue = "true")
public class SyntheticDataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataGenerator.class);

    private static final String[] GENRES = {"Pop", "Rock", "Hip-Hop", "Electronic", "Indie", "Metal", "Jazz", "Classical"};
    private static final String[] CATEGORIES = {"Concert", "Festival", "Tour"};
    private static final String[] ADJECTIVES = {"Velvet", "Neon", "Silent", "Golden", "Crimson", "Electric", "Midnight",
            "Paper", "Glass", "Wild", "Northern", "Hollow", "Silver", "Radiant", "Quiet"};
    private static final String[] NOUNS = {"Foxes", "Machines", "Harbour", "Echoes", "Lanterns", "Rivers", "Cathedral",
            "Wolves", "Orchestra", "Signal", "Compass", "Static", "Gardens", "Vultures", "Parade"};
    private static final String[][] CITIES = {
            {"Dublin", "Ireland", "Europe/Dublin"},
            {"London", "United Kingdom", "Europe/London"},
            {"Paris", "France", "Europe/Paris"},
            {"Berlin", "Germany", "Europe/Berlin"},
            {"Amsterdam", "Netherlands", "Europe/Amsterdam"},
            {"Madrid", "Spain", "Europe/Madrid"},
            {"Barcelona", "Spain", "Europe/Madrid"},
            {"Lisbon", "Portugal", "Europe/Lisbon"},
            {"Rome", "Italy", "Europe/Rome"},
            {"Milan", "Italy", "Europe/Rome"},
            {"Vienna", "Austria", "Europe/Vienna"},
            {"Copenhagen", "Denmark", "Europe/Copenhagen"},
            {"Stockholm", "Sweden", "Europe/Stockholm"},
            {"Warsaw", "Poland", "Europe/Warsaw"},
            {"Prague", "Czechia", "Europe/Prague"}
    };
    private static final String[] TIER_NAMES = {"Early Bird", "Standing", "Lower Tier", "Upper Tier", "VIP"};
    private static final int REFERENCE_PRICE_CENTS = 8_000;
    private static final int ORDER_BATCH_SIZE = 2_000;

    private final JdbcTemplate jdbc;
    private final TicketingProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SyntheticDataGenerator(JdbcTemplate jdbc,
                                  TicketingProperties properties,
                                  PasswordEncoder passwordEncoder,
                                  Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from artists", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Seed data already present, skipping generation");
            return;
        }

        TicketingProperties.Seed config = properties.seed();
        Random random = new Random(config.randomSeed());
        Instant now = Instant.now(clock);
        long startedAt = System.currentTimeMillis();

        List<Long> artistIds = insertArtists(config.artists(), random);
        List<Long> venueIds = insertVenues(config.venues(), random);
        List<Long> buyerIds = insertBuyers(config.buyers(), now);
        insertAdmin(now);

        List<EventRow> events = buildEvents(config, artistIds, venueIds, random, now);
        insertEvents(events);
        assignEventIds(events);

        List<TierRow> tiers = buildTiers(events, random);
        insertTiers(tiers);
        assignTierIds(tiers);

        int orders = generateSales(events, tiers, buyerIds, random, now);
        updateReservedQuantities(tiers);

        log.info("Seeded {} artists, {} venues, {} buyers, {} events, {} tiers, {} orders in {} ms",
                artistIds.size(), venueIds.size(), buyerIds.size(), events.size(), tiers.size(), orders,
                System.currentTimeMillis() - startedAt);
    }

    private List<Long> insertArtists(int count, Random random) {
        Set<String> names = new LinkedHashSet<>();
        while (names.size() < count) {
            names.add(ADJECTIVES[random.nextInt(ADJECTIVES.length)] + " " + NOUNS[random.nextInt(NOUNS.length)]);
        }
        List<String> nameList = new ArrayList<>(names);

        // A few genuine headliners, a long tail of everyone else.
        jdbc.batchUpdate("insert into artists (name, genre, popularity_score) values (?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, nameList.get(i));
                        ps.setString(2, GENRES[random.nextInt(GENRES.length)]);
                        ps.setInt(3, i < count * 0.15 ? 80 + random.nextInt(21) : 10 + random.nextInt(65));
                    }

                    @Override
                    public int getBatchSize() {
                        return nameList.size();
                    }
                });
        return jdbc.queryForList("select id from artists order by id", Long.class);
    }

    private List<Long> insertVenues(int count, Random random) {
        jdbc.batchUpdate("insert into venues (name, city, country, capacity, timezone) values (?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        String[] city = CITIES[i % CITIES.length];
                        ps.setString(1, city[0] + " Arena");
                        ps.setString(2, city[0]);
                        ps.setString(3, city[1]);
                        // Kept within reach of the buyer pool: if capacity outruns the number
                        // of distinct buyers, every event looks unsellable and the model would
                        // learn a ceiling that only exists in the generator.
                        ps.setInt(4, 1_500 + random.nextInt(6_500));
                        ps.setString(5, city[2]);
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });
        return jdbc.queryForList("select id from venues order by id", Long.class);
    }

    private List<Long> insertBuyers(int count, Instant now) {
        // Hashing once and reusing keeps seeding to seconds; bcrypt per row would take minutes.
        String sharedHash = passwordEncoder.encode("password123");
        LocalDateTime createdAt = utc(now);

        jdbc.batchUpdate("""
                        insert into users (email, password_hash, display_name, role, created_at)
                        values (?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, "buyer" + i + "@example.com");
                        ps.setString(2, sharedHash);
                        ps.setString(3, "Buyer " + i);
                        ps.setString(4, "USER");
                        ps.setObject(5, createdAt);
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });
        return jdbc.queryForList("select id from users order by id", Long.class);
    }

    private void insertAdmin(Instant now) {
        Integer exists = jdbc.queryForObject(
                "select count(*) from users where email = ?", Integer.class, "admin@fairticketing.local");
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.update("""
                        insert into users (email, password_hash, display_name, role, created_at)
                        values (?, ?, ?, 'ADMIN', ?)
                        """,
                "admin@fairticketing.local",
                passwordEncoder.encode("password123"),
                "Admin",
                utc(now));
    }

    private List<EventRow> buildEvents(TicketingProperties.Seed config,
                                       List<Long> artistIds,
                                       List<Long> venueIds,
                                       Random random,
                                       Instant now) {
        List<EventRow> events = new ArrayList<>();
        List<Integer> popularity = jdbc.queryForList("select popularity_score from artists order by id", Integer.class);
        List<Integer> capacity = jdbc.queryForList("select capacity from venues order by id", Integer.class);
        List<String> cities = jdbc.queryForList("select city from venues order by id", String.class);

        for (int i = 0; i < config.pastEvents() + config.upcomingEvents(); i++) {
            boolean past = i < config.pastEvents();
            int artistIndex = random.nextInt(artistIds.size());
            int venueIndex = random.nextInt(venueIds.size());

            Instant startsAt = past
                    ? now.minus(Duration.ofDays(30 + random.nextInt(510)))
                    : now.plus(Duration.ofDays(20 + random.nextInt(100)));
            int leadTimeDays = 45 + random.nextInt(135);
            Instant salesStartAt = startsAt.minus(Duration.ofDays(leadTimeDays));
            Instant salesEndAt = startsAt.minus(Duration.ofHours(2));

            String status = past ? "CLOSED" : (salesStartAt.isBefore(now) ? "ON_SALE" : "DRAFT");
            int artistPopularity = popularity.get(artistIndex);

            EventRow event = new EventRow();
            event.artistId = artistIds.get(artistIndex);
            event.venueId = venueIds.get(venueIndex);
            event.title = "Live in " + cities.get(venueIndex);
            event.category = CATEGORIES[random.nextInt(CATEGORIES.length)];
            event.status = status;
            event.startsAt = startsAt;
            event.salesStartAt = salesStartAt;
            event.salesEndAt = salesEndAt;
            event.popularity = artistPopularity;
            event.capacity = capacity.get(venueIndex);
            event.leadTimeDays = leadTimeDays;
            event.weekend = isWeekend(startsAt);
            // Turned on for the acts a forecast would flag as heavily oversubscribed.
            event.waitingRoom = artistPopularity >= 80;
            events.add(event);
        }
        return events;
    }

    private void insertEvents(List<EventRow> events) {
        jdbc.batchUpdate("""
                        insert into events (artist_id, venue_id, title, category, status,
                                            starts_at, sales_start_at, sales_end_at,
                                            waiting_room_enabled, created_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        EventRow event = events.get(i);
                        ps.setLong(1, event.artistId);
                        ps.setLong(2, event.venueId);
                        ps.setString(3, event.title);
                        ps.setString(4, event.category);
                        ps.setString(5, event.status);
                        ps.setObject(6, utc(event.startsAt));
                        ps.setObject(7, utc(event.salesStartAt));
                        ps.setObject(8, utc(event.salesEndAt));
                        ps.setBoolean(9, event.waitingRoom);
                        ps.setObject(10, utc(event.salesStartAt));
                    }

                    @Override
                    public int getBatchSize() {
                        return events.size();
                    }
                });
    }

    private void assignEventIds(List<EventRow> events) {
        List<Long> ids = jdbc.queryForList("select id from events order by id", Long.class);
        for (int i = 0; i < events.size(); i++) {
            events.get(i).id = ids.get(i);
        }
    }

    private List<TierRow> buildTiers(List<EventRow> events, Random random) {
        List<TierRow> tiers = new ArrayList<>();
        for (EventRow event : events) {
            int tierCount = 3 + random.nextInt(3);
            int remaining = event.capacity;

            for (int i = 0; i < tierCount; i++) {
                boolean last = i == tierCount - 1;
                int quantity = last ? remaining : Math.max(200, (int) (event.capacity * (0.15 + random.nextDouble() * 0.2)));
                quantity = Math.min(quantity, remaining);
                remaining -= quantity;
                if (quantity <= 0) {
                    continue;
                }

                TierRow tier = new TierRow();
                tier.event = event;
                tier.name = TIER_NAMES[i];
                tier.priceCents = (int) (REFERENCE_PRICE_CENTS * (0.5 + i * 0.45) * (0.8 + random.nextDouble() * 0.5));
                tier.totalQuantity = quantity;
                tiers.add(tier);
                event.tiers.add(tier);
            }
        }
        return tiers;
    }

    private void insertTiers(List<TierRow> tiers) {
        jdbc.batchUpdate("""
                        insert into ticket_tiers (event_id, name, price_cents, currency,
                                                  total_quantity, reserved_quantity, max_per_user, version)
                        values (?, ?, ?, 'EUR', ?, 0, ?, 0)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        TierRow tier = tiers.get(i);
                        ps.setLong(1, tier.event.id);
                        ps.setString(2, tier.name);
                        ps.setInt(3, tier.priceCents);
                        ps.setInt(4, tier.totalQuantity);
                        ps.setInt(5, 4);
                    }

                    @Override
                    public int getBatchSize() {
                        return tiers.size();
                    }
                });
    }

    /** Events hold references to the same TierRow instances, so they see the ids too. */
    private void assignTierIds(List<TierRow> tiers) {
        List<Long> ids = jdbc.queryForList("select id from ticket_tiers order by id", Long.class);
        for (int i = 0; i < tiers.size(); i++) {
            tiers.get(i).id = ids.get(i);
        }
    }

    /**
     * Produces the orders themselves rather than a summary, because the model
     * needs how fast an event sold, not only how much of it sold.
     */
    private int generateSales(List<EventRow> events,
                              List<TierRow> tiers,
                              List<Long> buyerIds,
                              Random random,
                              Instant now) {
        List<Object[]> pending = new ArrayList<>();
        int orderNo = 0;

        for (EventRow event : events) {
            if (event.tiers.isEmpty() || "DRAFT".equals(event.status)) {
                continue;
            }

            // An event still selling has only realised part of its demand so far.
            double windowProgress = "CLOSED".equals(event.status)
                    ? 1.0
                    : progress(event.salesStartAt, event.salesEndAt, now);
            if (windowProgress <= 0) {
                continue;
            }

            List<Long> pool = new ArrayList<>(buyerIds);
            Collections.shuffle(pool, random);
            int poolIndex = 0;

            for (TierRow tier : event.tiers) {
                double ratio = DemandProfile.expectedSellThrough(
                        event.popularity, event.capacity, tier.priceCents,
                        REFERENCE_PRICE_CENTS, event.weekend, event.leadTimeDays);
                double noise = 0.85 + random.nextDouble() * 0.3;
                int target = (int) Math.min(tier.totalQuantity, tier.totalQuantity * ratio * noise * windowProgress);

                // Hot events sell in a burst at the start; quiet ones trickle.
                double frontLoading = 1.0 + Math.max(0.0, ratio - 0.6) * 6.0;

                int sold = 0;
                while (sold < target && poolIndex < pool.size()) {
                    int quantity = Math.min(1 + random.nextInt(4), target - sold);
                    Instant placedAt = sampleSaleTime(event, random, frontLoading, windowProgress);

                    pending.add(new Object[]{
                            "SEED-" + orderNo,
                            pool.get(poolIndex),
                            event.id,
                            tier.id,
                            quantity,
                            tier.priceCents,
                            tier.priceCents * quantity,
                            "seed-" + orderNo,
                            pool.get(poolIndex) + ":" + event.id,
                            utc(placedAt)
                    });

                    sold += quantity;
                    poolIndex++;
                    orderNo++;

                    if (pending.size() >= ORDER_BATCH_SIZE) {
                        flushOrders(pending);
                    }
                }
                tier.soldQuantity = sold;
            }
        }
        flushOrders(pending);
        return orderNo;
    }

    private void flushOrders(List<Object[]> pending) {
        if (pending.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                        insert into orders (order_no, user_id, event_id, tier_id, quantity,
                                            unit_price_cents, total_cents, status, idempotency_key,
                                            active_lock_key, created_at, paid_at, completed_at, version)
                        values (?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, 0)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Object[] row = pending.get(i);
                        ps.setString(1, (String) row[0]);
                        ps.setLong(2, (Long) row[1]);
                        ps.setLong(3, (Long) row[2]);
                        ps.setLong(4, (Long) row[3]);
                        ps.setInt(5, (Integer) row[4]);
                        ps.setInt(6, (Integer) row[5]);
                        ps.setInt(7, (Integer) row[6]);
                        ps.setString(8, (String) row[7]);
                        ps.setString(9, (String) row[8]);
                        ps.setObject(10, row[9]);
                        ps.setObject(11, row[9]);
                        ps.setObject(12, row[9]);
                    }

                    @Override
                    public int getBatchSize() {
                        return pending.size();
                    }
                });
        pending.clear();
    }

    private void updateReservedQuantities(List<TierRow> tiers) {
        jdbc.batchUpdate("update ticket_tiers set reserved_quantity = ? where id = ?",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setInt(1, tiers.get(i).soldQuantity);
                        ps.setLong(2, tiers.get(i).id);
                    }

                    @Override
                    public int getBatchSize() {
                        return tiers.size();
                    }
                });
    }

    private Instant sampleSaleTime(EventRow event, Random random, double frontLoading, double windowProgress) {
        long windowSeconds = Duration.between(event.salesStartAt, event.salesEndAt).getSeconds();
        double position = Math.pow(random.nextDouble(), frontLoading) * windowProgress;
        return event.salesStartAt.plusSeconds((long) (windowSeconds * position));
    }

    private static double progress(Instant from, Instant to, Instant now) {
        if (!now.isAfter(from)) {
            return 0.0;
        }
        if (now.isAfter(to)) {
            return 1.0;
        }
        return (double) Duration.between(from, now).getSeconds() / Duration.between(from, to).getSeconds();
    }

    private static boolean isWeekend(Instant instant) {
        DayOfWeek day = instant.atZone(ZoneOffset.UTC).getDayOfWeek();
        return day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static final class EventRow {
        Long id;
        Long artistId;
        Long venueId;
        String title;
        String category;
        String status;
        Instant startsAt;
        Instant salesStartAt;
        Instant salesEndAt;
        int popularity;
        int capacity;
        int leadTimeDays;
        boolean weekend;
        boolean waitingRoom;
        final List<TierRow> tiers = new ArrayList<>();
    }

    private static final class TierRow {
        Long id;
        EventRow event;
        String name;
        int priceCents;
        int totalQuantity;
        int soldQuantity;
    }
}
