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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the buying flow over HTTP, through the real security filters and the
 * real error handling. The concurrency tests call the service directly, so
 * without this the entire web layer would be unverified.
 */
@AutoConfigureMockMvc
class BuyingTicketsApiIT extends AbstractIntegrationTest {

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

    private Long eventId;
    private Long standingTierId;
    private Long seatedTierId;
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
        Artist artist = artists.save(new Artist("The Silent Harbour", "Indie", 88));
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
        eventId = events.save(event).getId();

        standingTierId = tiers.save(tier(event, "Standing", 5_000, 50)).getId();
        seatedTierId = tiers.save(tier(event, "Seated", 9_000, 20)).getId();

        buyerToken = register("buyer@example.com");
    }

    @Nested
    @DisplayName("access")
    class Access {

        @Test
        @DisplayName("a rejected request answers in the same shape as every other error")
        void buying_requires_a_token() throws Exception {
            http.perform(post("/api/orders")
                            .header("Idempotency-Key", "no-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody(standingTierId, 1)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        void a_forged_token_is_rejected() throws Exception {
            http.perform(post("/api/orders")
                            .header("Authorization", "Bearer not.a.real.token")
                            .header("Idempotency-Key", "forged")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody(standingTierId, 1)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        void browsing_does_not() throws Exception {
            http.perform(get("/api/events")).andExpect(status().isOk());
            http.perform(get("/api/events/" + eventId)).andExpect(status().isOk());
        }

        @Test
        void a_registered_email_cannot_be_reused() throws Exception {
            http.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"buyer@example.com","password":"password123","displayName":"Copy"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        }

        @Test
        void a_wrong_password_is_rejected() throws Exception {
            http.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"buyer@example.com","password":"not-the-password"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("browsing")
    class Browsing {

        @Test
        void the_listing_reports_what_is_left_and_the_cheapest_seat() throws Exception {
            http.perform(get("/api/events").param("city", "dublin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("Live in Dublin"))
                    .andExpect(jsonPath("$.content[0].artistName").value("The Silent Harbour"))
                    .andExpect(jsonPath("$.content[0].ticketsAvailable").value(70))
                    .andExpect(jsonPath("$.content[0].lowestPriceCents").value(5_000));
        }

        @Test
        void a_city_with_no_events_returns_an_empty_page_rather_than_an_error() throws Exception {
            http.perform(get("/api/events").param("city", "Reykjavik"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void a_price_ceiling_below_every_tier_hides_the_show() throws Exception {
            http.perform(get("/api/events").param("maxPriceCents", "4000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void a_price_floor_still_matches_if_any_tier_is_in_range() throws Exception {
            http.perform(get("/api/events").param("minPriceCents", "8000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("Live in Dublin"));
        }

        @Test
        void a_date_window_after_the_show_returns_nothing() throws Exception {
            http.perform(get("/api/events").param("from", Instant.now().plus(Duration.ofDays(90)).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("tiers come back cheapest first, in a stable order")
        void the_detail_page_lists_every_tier() throws Exception {
            http.perform(get("/api/events/" + eventId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tiers.length()").value(2))
                    .andExpect(jsonPath("$.tiers[0].name").value("Standing"))
                    .andExpect(jsonPath("$.tiers[0].priceCents").value(5_000))
                    .andExpect(jsonPath("$.tiers[0].availableQuantity").value(50))
                    .andExpect(jsonPath("$.tiers[0].soldOut").value(false))
                    .andExpect(jsonPath("$.tiers[1].name").value("Seated"));
        }

        @Test
        void an_unknown_event_is_a_not_found_rather_than_a_crash() throws Exception {
            http.perform(get("/api/events/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("checkout")
    class Checkout {

        @Test
        void holding_tickets_returns_an_order_awaiting_payment() throws Exception {
            checkout(buyerToken, standingTierId, 2, "key-1")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.quantity").value(2))
                    .andExpect(jsonPath("$.totalCents").value(10_000))
                    .andExpect(jsonPath("$.expiresAt").exists());

            assertThat(tiers.findById(standingTierId).orElseThrow().availableQuantity()).isEqualTo(48);
        }

        @Test
        @DisplayName("a retried request returns the first order rather than buying twice")
        void repeating_the_idempotency_key_does_not_buy_twice() throws Exception {
            String first = orderNoFrom(checkout(buyerToken, standingTierId, 2, "key-1")
                    .andExpect(status().isCreated()));
            String second = orderNoFrom(checkout(buyerToken, standingTierId, 2, "key-1"));

            assertThat(second).isEqualTo(first);
            assertThat(orders.count()).isEqualTo(1);
            assertThat(tiers.findById(standingTierId).orElseThrow().availableQuantity()).isEqualTo(48);
        }

        @Test
        @DisplayName("a request with no idempotency key is refused, not silently accepted")
        void the_idempotency_key_is_required() throws Exception {
            http.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + buyerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody(standingTierId, 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void buying_more_than_the_limit_is_refused() throws Exception {
            checkout(buyerToken, standingTierId, 9, "key-1")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PURCHASE_LIMIT_EXCEEDED"));

            assertThat(orders.count()).isZero();
        }

        @Test
        void a_quantity_of_zero_is_refused_before_it_reaches_the_service() throws Exception {
            checkout(buyerToken, standingTierId, 0, "key-1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("one live order per event, even across different tiers")
        void a_second_order_for_the_same_event_is_refused() throws Exception {
            checkout(buyerToken, standingTierId, 1, "key-1").andExpect(status().isCreated());

            checkout(buyerToken, seatedTierId, 1, "key-2")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_ACTIVE_ORDER"));

            // The stock taken for the refused order has to come back.
            assertThat(tiers.findById(seatedTierId).orElseThrow().availableQuantity()).isEqualTo(20);
        }

        @Test
        void an_unknown_tier_is_a_not_found() throws Exception {
            checkout(buyerToken, 999999L, 1, "key-1")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("a malformed body is the caller's fault and is reported as such")
        void unreadable_json_is_not_a_server_error() throws Exception {
            http.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + buyerToken)
                            .header("Idempotency-Key", "broken-json")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tierId\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        void a_wrongly_typed_parameter_is_not_a_server_error() throws Exception {
            http.perform(get("/api/events/not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("after checkout")
    class AfterCheckout {

        @Test
        void paying_issues_the_tickets_and_the_order_shows_up_in_the_history() throws Exception {
            String orderNo = orderNoFrom(checkout(buyerToken, standingTierId, 2, "key-1"));

            http.perform(post("/api/orders/" + orderNo + "/pay")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.paidAt").exists());

            http.perform(get("/api/orders").header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].orderNo").value(orderNo));
        }

        @Test
        void cancelling_puts_the_tickets_back_on_sale() throws Exception {
            String orderNo = orderNoFrom(checkout(buyerToken, standingTierId, 2, "key-1"));

            http.perform(post("/api/orders/" + orderNo + "/cancel")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            assertThat(tiers.findById(standingTierId).orElseThrow().availableQuantity()).isEqualTo(50);
            assertThat(ledger.netDeltaForTier(standingTierId)).isZero();
        }

        @Test
        void a_cancelled_order_cannot_then_be_paid() throws Exception {
            String orderNo = orderNoFrom(checkout(buyerToken, standingTierId, 1, "key-1"));
            http.perform(post("/api/orders/" + orderNo + "/cancel")
                    .header("Authorization", "Bearer " + buyerToken)).andExpect(status().isOk());

            http.perform(post("/api/orders/" + orderNo + "/pay")
                            .header("Authorization", "Bearer " + buyerToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
        }

        @Test
        @DisplayName("someone else's order is reported as missing, not as forbidden")
        void orders_are_not_visible_to_other_buyers() throws Exception {
            String orderNo = orderNoFrom(checkout(buyerToken, standingTierId, 1, "key-1"));
            String otherToken = register("stranger@example.com");

            http.perform(get("/api/orders/" + orderNo).header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));

            http.perform(post("/api/orders/" + orderNo + "/cancel")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
    }

    private ResultActions checkout(String token, Long tierId, int quantity, String idempotencyKey) throws Exception {
        return http.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkoutBody(tierId, quantity)));
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

    private static String orderNoFrom(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.orderNo");
    }

    private static String checkoutBody(Long tierId, int quantity) {
        return """
                {"tierId":%d,"quantity":%d}
                """.formatted(tierId, quantity);
    }

    private static TicketTier tier(Event event, String name, int priceCents, int quantity) {
        TicketTier tier = new TicketTier();
        tier.setEvent(event);
        tier.setName(name);
        tier.setPriceCents(priceCents);
        tier.setCurrency("EUR");
        tier.setTotalQuantity(quantity);
        tier.setReservedQuantity(0);
        tier.setMaxPerUser(4);
        return tier;
    }
}
