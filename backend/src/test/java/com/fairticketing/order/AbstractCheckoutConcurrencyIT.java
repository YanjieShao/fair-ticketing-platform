package com.fairticketing.order;

import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.repository.UserAccountRepository;
import com.fairticketing.common.config.TicketingProperties.InventoryStrategy;
import com.fairticketing.common.error.BusinessException;
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
import com.fairticketing.inventory.service.InventoryReconciliationService;
import com.fairticketing.inventory.service.InventoryReconciliationService.Reconciliation;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.order.service.OrderService;
import com.fairticketing.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The claim this project makes is that it does not oversell. This is where that
 * claim is checked: far more buyers than tickets, all released at once.
 *
 * <p>Subclasses pin a different inventory strategy, so every implementation has
 * to pass the same bar.
 */
abstract class AbstractCheckoutConcurrencyIT extends AbstractIntegrationTest {

    private static final int STOCK = 100;
    private static final int BUYERS = 500;
    private static final int THREADS = 32;

    @Autowired
    private OrderService orderService;
    @Autowired
    private InventoryService inventoryService;
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
    private InventoryLedgerRepository ledger;
    @Autowired
    private InventoryReconciliationService reconciliation;
    @Autowired
    private StringRedisTemplate redis;

    private Long tierId;
    private List<Long> buyerIds;

    protected abstract InventoryStrategy expectedStrategy();

    @BeforeEach
    void seedOneOversubscribedTier() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        orders.deleteAllInBatch();
        ledger.deleteAllInBatch();
        tiers.deleteAllInBatch();
        events.deleteAllInBatch();
        artists.deleteAllInBatch();
        venues.deleteAllInBatch();
        users.deleteAllInBatch();

        Instant now = Instant.now();
        Artist artist = artists.save(new Artist("The Silent Harbour", "Indie", 92));
        Venue venue = venues.save(new Venue("Test Arena", "Dublin", "Ireland", 5_000, "Europe/Dublin"));

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
        tier.setTotalQuantity(STOCK);
        tier.setReservedQuantity(0);
        tier.setMaxPerUser(4);
        tierId = tiers.save(tier).getId();

        List<UserAccount> accounts = new ArrayList<>(BUYERS);
        for (int i = 0; i < BUYERS; i++) {
            accounts.add(new UserAccount("rush" + i + "@example.com", "hash", "Buyer " + i, UserRole.USER, now));
        }
        buyerIds = users.saveAll(accounts).stream().map(UserAccount::getId).toList();
    }

    @Test
    @DisplayName("500 buyers race for 100 tickets and exactly 100 get one")
    void never_oversells() throws Exception {
        assertThat(inventoryService.activeStrategy()).isEqualTo(expectedStrategy());

        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger sold = new AtomicInteger();
        Map<String, Integer> rejections = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        List<Future<?>> attempts = new ArrayList<>(BUYERS);
        for (int i = 0; i < BUYERS; i++) {
            Long buyerId = buyerIds.get(i);
            String key = "rush-key-" + i;
            attempts.add(pool.submit(() -> {
                startGun.await();
                try {
                    orderService.checkout(buyerId, tierId, 1, key);
                    sold.incrementAndGet();
                } catch (Exception rejection) {
                    rejections.merge(describe(rejection), 1, Integer::sum);
                }
                return null;
            }));
        }

        startGun.countDown();
        for (Future<?> attempt : attempts) {
            attempt.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Everyone who missed out must have missed out because the tier ran dry.
        // Any other reason means the system dropped a sale it could have made.
        assertThat(rejections)
                .as("reasons buyers were turned away")
                .containsOnlyKeys("SOLD_OUT");
        assertThat(sold.get()).isEqualTo(STOCK);
        assertThat(rejections.get("SOLD_OUT")).isEqualTo(BUYERS - STOCK);
        assertThat(inventoryService.remaining(tierId)).isZero();

        // Rolled back attempts must leave nothing behind.
        assertThat(orders.count()).isEqualTo(STOCK);
        assertThat(orders.findAll()).allSatisfy(order ->
                assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT));
        assertThat(ledger.netDeltaForTier(tierId)).isEqualTo(STOCK);

        // The ledger is what actually happened. Under the Redis strategy the tier
        // row is not written on the hot path and only catches up here, so the
        // comparison is the point rather than an afterthought.
        Reconciliation result = reconciliation.reconcileTier(tierId);
        assertThat(result.fromLedger()).isEqualTo(STOCK);
        if (result.redisCounter() != null) {
            assertThat(result.redisCounter()).isEqualTo(result.redisExpected()).isZero();
        }

        TicketTier tier = tiers.findById(tierId).orElseThrow();
        assertThat(tier.getReservedQuantity()).isEqualTo(STOCK);
        assertThat(tier.isSoldOut()).isTrue();
    }

    private static String describe(Exception rejection) {
        if (rejection instanceof BusinessException business) {
            return business.code().name();
        }
        Throwable root = rejection;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
