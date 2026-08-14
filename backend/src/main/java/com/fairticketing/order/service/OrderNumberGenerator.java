package com.fairticketing.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.random.RandomGenerator;

/**
 * Order numbers are shown to buyers and quoted in support requests, so they are
 * short and readable rather than a raw UUID. Uniqueness is still guaranteed by
 * the unique index, not by hoping the random part never repeats.
 */
@Component
public class OrderNumberGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SUFFIX_LENGTH = 8;

    private final Clock clock;
    private final RandomGenerator random;

    @Autowired
    public OrderNumberGenerator(Clock clock) {
        this(clock, RandomGenerator.getDefault());
    }

    /** Lets tests pin the random source. */
    OrderNumberGenerator(Clock clock, RandomGenerator random) {
        this.clock = clock;
        this.random = random;
    }

    public String next() {
        LocalDate today = LocalDate.now(clock);
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "FT%04d%02d%02d%s".formatted(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(), suffix);
    }
}
