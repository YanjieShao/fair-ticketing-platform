package com.fairticketing.waitingroom;

import com.fairticketing.auth.repository.UserAccountRepository;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.event.repository.ArtistRepository;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.repository.VenueRepository;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.InventoryLedgerRepository;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.payment.repository.PaymentRepository;
import com.fairticketing.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Burst of one and a rate of zero: the first arrival is let through, everyone
 * after them waits, and waiting cannot be skipped by calling checkout directly.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ticketing.waiting-room.enabled=true",
        "ticketing.waiting-room.burst=1",
        "ticketing.waiting-room.admit-rate-per-second=0"
})
class WaitingRoomCheckoutIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc http;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private ArtistRepository artists;
    @Autowired
    private VenueRepository venues;
    @Autowired
    private EventRepository events;
    @Autowired
    private TicketTierRepository tiers;
    @Autowired
    private TicketOrderRepository orders;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private InventoryLedgerRepository ledger;

    private Long eventId;
    private Long tierId;
    private String firstToken;
    private String secondToken;

    @BeforeEach
    void seed() throws Exception {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        payments.deleteAllInBatch();
        orders.deleteAllInBatch();
        ledger.deleteAllInBatch();
        tiers.deleteAllInBatch();
        events.deleteAllInBatch();
        artists.deleteAllInBatch();
        venues.deleteAllInBatch();
        users.deleteAllInBatch();

        Instant now = Instant.now();
        Artist artist = artists.save(new Artist("The Silent Harbour", "Indie", 90));
        Venue venue = venues.save(new Venue("Test Arena", "Dublin", "Ireland", 2_000, "Europe/Dublin"));
        Event event = new Event();
        event.setArtist(artist);
        event.setVenue(venue);
        event.setTitle("Live in Dublin");
        event.setCategory("Concert");
        event.setStatus(EventStatus.ON_SALE);
        event.setStartsAt(now.plus(Duration.ofDays(60)));
        event.setSalesStartAt(now.minus(Duration.ofDays(1)));
        event.setSalesEndAt(now.plus(Duration.ofDays(59)));
        event.setWaitingRoomEnabled(true);
        event.setCreatedAt(now);
        eventId = events.save(event).getId();

        TicketTier tier = new TicketTier();
        tier.setEvent(event);
        tier.setName("Standing");
        tier.setPriceCents(5_000);
        tier.setCurrency("EUR");
        tier.setTotalQuantity(50);
        tier.setReservedQuantity(0);
        tier.setMaxPerUser(4);
        tierId = tiers.save(tier).getId();

        firstToken = register("first@example.com");
        secondToken = register("second@example.com");
    }

    @Test
    @DisplayName("checkout without a pass is refused before stock is touched")
    void checkout_requires_admission() throws Exception {
        http.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("Idempotency-Key", "jump")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WAITING_ROOM_TOKEN_REQUIRED"));
    }

    @Test
    void the_first_arrival_is_admitted_and_can_buy() throws Exception {
        http.perform(post("/api/waiting-room/" + eventId + "/join")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITTED"));

        http.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("Idempotency-Key", "admitted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void later_arrivals_wait_and_cannot_skip_the_line() throws Exception {
        http.perform(post("/api/waiting-room/" + eventId + "/join")
                .header("Authorization", "Bearer " + firstToken)).andExpect(status().isOk());

        http.perform(post("/api/waiting-room/" + eventId + "/join")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));

        http.perform(get("/api/waiting-room/" + eventId)
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        http.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + secondToken)
                        .header("Idempotency-Key", "skip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WAITING_ROOM_TOKEN_REQUIRED"));
    }

    private String register(String email) throws Exception {
        String body = http.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Test Buyer"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String checkoutBody() {
        return """
                {"tierId":%d,"quantity":1}
                """.formatted(tierId);
    }
}
