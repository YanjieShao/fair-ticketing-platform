package com.fairticketing.api;

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
import com.fairticketing.common.config.TicketingProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tightens the per-account cap so a third checkout is a 429. Other HTTP tests
 * keep the default of 8/minute, which a single happy-path buyer does not hit.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ticketing.rate-limit.enabled=true",
        "ticketing.rate-limit.checkout-per-minute=2",
        "ticketing.rate-limit.burst-per-ten-seconds=20"
})
class RateLimitApiIT extends AbstractIntegrationTest {

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
    private TicketingProperties properties;

    private Long standingTierId;
    private String buyerToken;

    @BeforeEach
    void seedAnOnSaleEvent() throws Exception {
        payments.deleteAllInBatch();
        orders.deleteAllInBatch();
        ledger.deleteAllInBatch();
        tiers.deleteAllInBatch();
        events.deleteAllInBatch();
        artists.deleteAllInBatch();
        venues.deleteAllInBatch();
        users.deleteAllInBatch();

        Instant now = Instant.now();
        Artist artist = artists.save(new Artist("Rate Limit Act", "Pop", 40));
        Venue venue = venues.save(new Venue("Rate Limit Hall", "Dublin", "Ireland", 500, "Europe/Dublin"));

        Event event = new Event();
        event.setArtist(artist);
        event.setVenue(venue);
        event.setTitle("Rate limit on-sale");
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
        tier.setTotalQuantity(50);
        tier.setReservedQuantity(0);
        tier.setMaxPerUser(4);
        standingTierId = tiers.save(tier).getId();

        buyerToken = register("limited@example.com");
    }

    @Test
    void the_test_context_uses_the_tight_caps() {
        assertThat(properties.rateLimit().enabled()).isTrue();
        assertThat(properties.rateLimit().checkoutPerMinute()).isEqualTo(2);
    }

    @Test
    void a_third_checkout_from_the_same_account_is_rate_limited() throws Exception {
        checkout("key-1").andExpect(status().isCreated());
        checkout("key-2").andExpect(status().isCreated());
        checkout("key-3")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void another_account_is_not_punished_for_the_first_buyers_retries() throws Exception {
        checkout("solo-1").andExpect(status().isCreated());
        checkout("solo-2").andExpect(status().isCreated());
        checkout("solo-3").andExpect(status().isTooManyRequests());

        String other = register("other-limited@example.com");
        http.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + other)
                        .header("Idempotency-Key", "other-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(standingTierId)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions checkout(String key) throws Exception {
        return http.perform(post("/api/orders")
                .header("Authorization", "Bearer " + buyerToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(standingTierId)));
    }

    private String register(String email) throws Exception {
        String body = http.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","displayName":"Limited"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String body(Long tierId) {
        return "{\"tierId\":%d,\"quantity\":1}".formatted(tierId);
    }
}
