package com.fairticketing.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real MySQL, because the behaviour worth testing here (row
 * locks, unique indexes, foreign keys) does not exist in an in-memory database.
 * One container is shared by every integration test in the run.
 */
@SpringBootTest
@Testcontainers
// Pinned rather than left to application.yml: the seeder is switched on by an
// environment variable, and a developer who happens to have it exported should
// not end up running a 200-second data generator inside test setup.
@TestPropertySource(properties = "ticketing.seed.enabled=false")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
}
