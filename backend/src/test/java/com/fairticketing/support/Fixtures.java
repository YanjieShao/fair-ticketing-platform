package com.fairticketing.support;

import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.WaitlistShowView;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    private Fixtures() {
    }

    public static TicketingProperties properties() {
        return properties(InventoryStrategy.DB_PESSIMISTIC_LOCK, 0.0);
    }

    public static TicketingProperties properties(InventoryStrategy strategy, double paymentFailureRate) {
        return new TicketingProperties(
                new TicketingProperties.Inventory(strategy),
                new TicketingProperties.Order(Duration.ofMinutes(10), 4),
                new TicketingProperties.Waitlist(Duration.ofMinutes(15)),
                new TicketingProperties.WaitingRoom(false, 20, 50, Duration.ofMinutes(5), 200, Duration.ofHours(12)),
                new TicketingProperties.Payment(paymentFailureRate),
                new TicketingProperties.Security("test-secret-that-is-long-enough-32", Duration.ofHours(2)),
                new TicketingProperties.Seed(false, 0, 0, 0, 0, 0, 1L),
                new TicketingProperties.Cors(List.of("http://localhost:5173")),
                new TicketingProperties.Ml("http://127.0.0.1:9", Duration.ofSeconds(1), false),
                new TicketingProperties.Llm("", "http://127.0.0.1:9", "gpt-4o-mini", Duration.ofSeconds(1), false),
                new TicketingProperties.LoadTest(false),
                new TicketingProperties.RateLimit(false, 8, 20, 5));
    }

    public static Jwt userJwt(long userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(String.valueOf(userId))
                .claim("email", "buyer@example.com")
                .build();
    }

    public static Event onSaleEvent(long id) {
        Artist artist = new Artist("The Silent Harbour", "Indie", 70);
        artist.setId(3L);
        Venue venue = new Venue("3Arena", "Dublin", "Ireland", 13_000, "Europe/Dublin");
        venue.setId(4L);
        Event event = new Event();
        event.setId(id);
        event.setArtist(artist);
        event.setVenue(venue);
        event.setTitle("Live in Dublin");
        event.setCategory("Concert");
        event.setStatus(EventStatus.ON_SALE);
        event.setStartsAt(NOW.plus(Duration.ofDays(60)));
        event.setSalesStartAt(NOW.minus(Duration.ofDays(1)));
        event.setSalesEndAt(NOW.plus(Duration.ofDays(59)));
        event.setWaitingRoomEnabled(false);
        event.setCreatedAt(NOW);
        return event;
    }

    public static TicketTier standing(Event event, long tierId) {
        TicketTier tier = new TicketTier();
        tier.setId(tierId);
        tier.setEvent(event);
        tier.setName("Standing");
        tier.setPriceCents(5_000);
        tier.setCurrency("EUR");
        tier.setTotalQuantity(100);
        tier.setReservedQuantity(10);
        tier.setMaxPerUser(4);
        return tier;
    }

    public static WaitlistShowView showView(long tierId) {
        return new WaitlistShowView(
                tierId, "Standing", 5L, "Live in Dublin", "The Silent Harbour",
                "3Arena", "Dublin", NOW.plus(Duration.ofDays(60)), "Europe/Dublin");
    }

    public static WaitlistShowView showViewWithoutTimezone(long tierId) {
        return new WaitlistShowView(
                tierId, "Standing", 5L, "Live in Dublin", "The Silent Harbour",
                "3Arena", "Dublin", NOW.plus(Duration.ofDays(60)), "  ");
    }
}
