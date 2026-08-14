package com.fairticketing.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Every component reads time through this bean rather than calling
 * {@code Instant.now()}, so payment windows and offer expiry can be exercised
 * in tests without sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
