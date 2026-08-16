package com.fairticketing.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * One MySQL and one Redis for the whole test JVM.
 *
 * <p>Each {@code @TestPropertySource} starts a new Spring context. Spring Boot's
 * Testcontainers support would otherwise close the first MySQL when that context
 * is replaced, leaving a later class (BuyingTicketsApiIT) holding a DataSource
 * to a dead port. Reuse is enabled from the Failsafe plugin so Ryuk does not
 * collect the container between classes.
 *
 * <p>GitHub Actions sets {@code FT_IT_EXTERNAL=true} and provides MySQL/Redis as
 * job services instead, so this class is not loaded there.
 */
final class SharedTestContainers {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withReuse(true);

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    static {
        MYSQL.start();
        REDIS.start();
    }

    private SharedTestContainers() {
    }
}
