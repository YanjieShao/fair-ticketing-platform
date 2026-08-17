package com.fairticketing.order.service;

import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorTest {

    @Test
    void numbers_are_dated_and_use_the_readable_alphabet() {
        RandomGenerator random = new RandomGenerator() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }

            @Override
            public long nextLong() {
                return 0;
            }
        };
        String number = new OrderNumberGenerator(Clock.fixed(Fixtures.NOW, ZoneOffset.UTC), random).next();
        assertThat(number).isEqualTo("FT2026081422222222");
    }

    @Test
    void production_constructor_still_returns_the_FT_prefix() {
        String number = new OrderNumberGenerator(Clock.fixed(Fixtures.NOW, ZoneOffset.UTC)).next();
        assertThat(number).startsWith("FT20260814");
        assertThat(number).hasSize(18);
    }
}
