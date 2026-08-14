package com.fairticketing.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real MySQL, because the behaviour worth testing here (row
 * locks, unique indexes, foreign keys) does not exist in an in-memory database.
 * One container is shared by every integration test in the run.
 *
 * <p>Scheduling is off so the reconciliation job does not fire while a test
 * context is shutting down its Redis connection.
 */
@SpringBootTest
@Testcontainers
// Pinned rather than left to application.yml: the seeder is switched on by an
// environment variable, and a developer who happens to have it exported should
// not end up running a 200-second data generator inside test setup.
@TestPropertySource(properties = {
        "ticketing.seed.enabled=false",
        "spring.task.scheduling.enabled=false"
})
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
}
