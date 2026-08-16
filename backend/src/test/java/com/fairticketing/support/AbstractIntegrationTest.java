package com.fairticketing.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

/**
 * Runs against a real MySQL, because the behaviour worth testing here (row
 * locks, unique indexes, foreign keys) does not exist in an in-memory database.
 *
 * <p>Scheduling is off so the reconciliation job does not fire while a test
 * context is shutting down its Redis connection.
 *
 * <p>Spring Boot 4's config data overlay ignores an
 * {@code ApplicationContextInitializer} {@code addFirst} for
 * {@code spring.datasource.url}, which is why these properties are set on
 * {@code System} before the context starts. GitHub Actions sets
 * {@code FT_IT_EXTERNAL=true} and provides MySQL and Redis as job services, so
 * the suite then uses localhost from {@code application.yml} instead.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "ticketing.seed.enabled=false",
        "spring.task.scheduling.enabled=false"
})
public abstract class AbstractIntegrationTest {

    private static final List<String> TABLES = List.of(
            "notifications",
            "waitlist_entries",
            "payments",
            "orders",
            "inventory_ledger",
            "demand_forecasts",
            "ai_insights",
            "audit_logs",
            "ticket_tiers",
            "events",
            "artists",
            "venues",
            "users");

    static {
        if (!"true".equalsIgnoreCase(System.getenv("FT_IT_EXTERNAL"))) {
            System.setProperty("spring.datasource.url", SharedTestContainers.MYSQL.getJdbcUrl());
            System.setProperty("spring.datasource.username", SharedTestContainers.MYSQL.getUsername());
            System.setProperty("spring.datasource.password", SharedTestContainers.MYSQL.getPassword());
            System.setProperty("spring.data.redis.host", SharedTestContainers.REDIS.getHost());
            System.setProperty("spring.data.redis.port",
                    String.valueOf(SharedTestContainers.REDIS.getMappedPort(6379)));
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearSharedTables() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : TABLES) {
            // DELETE, not TRUNCATE: TRUNCATE resets AUTO_INCREMENT, so every test
            // reuses user id 1 and trips the per-account checkout cap.
            jdbc.execute("DELETE FROM " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
