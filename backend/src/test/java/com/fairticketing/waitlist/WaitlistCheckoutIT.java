package com.fairticketing.waitlist;

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
import com.fairticketing.notification.repository.NotificationRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Returned stock has to go to the person who queued, not to whoever hits
 * checkout first after a cancellation.
 */
@AutoConfigureMockMvc
class WaitlistCheckoutIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc http;
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
    @Autowired
    private NotificationRepository notifications;

    private Long tierId;
    private String holderToken;
    private String waiterToken;

    @BeforeEach
    void seedSoldOutTier() throws Exception {
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
        event.setCreatedAt(now);
        events.save(event);

        TicketTier tier = new TicketTier();
        tier.setEvent(event);
        tier.setName("Standing");
        tier.setPriceCents(5_000);
        tier.setCurrency("EUR");
        tier.setTotalQuantity(1);
        tier.setReservedQuantity(0);
        tier.setMaxPerUser(4);
        tierId = tiers.save(tier).getId();

        holderToken = register("holder@example.com");
        waiterToken = register("waiter@example.com");
    }

    @Test
    void joining_is_refused_while_seats_remain() throws Exception {
        http.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_NOT_NEEDED"));
    }

    @Test
    @DisplayName("cancelling the last ticket offers it to the head of the waitlist")
    void cancelled_stock_is_held_for_the_next_person() throws Exception {
        checkout(holderToken, "hold-1").andExpect(status().isCreated());

        http.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.peopleAhead").value(0));

        String orderNo = JsonPath.read(
                checkout(holderToken, "hold-1").andReturn().getResponse().getContentAsString(),
                "$.orderNo");

        http.perform(post("/api/orders/" + orderNo + "/cancel")
                        .header("Authorization", "Bearer " + holderToken))
                .andExpect(status().isOk());

        http.perform(get("/api/waitlist").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("OFFERED"));

        assertThat(notifications.count()).isEqualTo(1);
        assertThat(tiers.findById(tierId).orElseThrow().availableQuantity()).isZero();

        checkout(waiterToken, "from-waitlist")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1));
    }

    @Test
    void a_stranger_cannot_buy_a_ticket_held_for_the_waitlist() throws Exception {
        checkout(holderToken, "hold-1").andExpect(status().isCreated());
        http.perform(post("/api/waitlist")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(joinBody(1)))
                .andExpect(status().isCreated());

        String orderNo = JsonPath.read(
                checkout(holderToken, "hold-1").andReturn().getResponse().getContentAsString(),
                "$.orderNo");
        http.perform(post("/api/orders/" + orderNo + "/cancel")
                .header("Authorization", "Bearer " + holderToken)).andExpect(status().isOk());

        String stranger = register("stranger@example.com");
        checkout(stranger, "snatch")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));
    }

    private org.springframework.test.web.servlet.ResultActions checkout(String token, String key) throws Exception {
        return http.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tierId":%d,"quantity":1}
                        """.formatted(tierId)));
    }

    private String joinBody(int quantity) {
        return """
                {"tierId":%d,"quantity":%d}
                """.formatted(tierId, quantity);
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
}
