package com.fairticketing.seed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemandProfileTest {

    private static final int REFERENCE_PRICE = 8_000;

    @Test
    @DisplayName("a headline act in an arena is oversubscribed")
    void popular_acts_exceed_capacity() {
        double ratio = DemandProfile.expectedSellThrough(95, 20_000, REFERENCE_PRICE, REFERENCE_PRICE, true, 60);

        assertThat(ratio).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("an unknown act does not fill the room")
    void unknown_acts_leave_seats_empty() {
        double ratio = DemandProfile.expectedSellThrough(10, 20_000, REFERENCE_PRICE, REFERENCE_PRICE, false, 60);

        assertThat(ratio).isLessThan(1.0);
    }

    @Test
    void demand_rises_with_popularity() {
        double low = DemandProfile.expectedSellThrough(30, 10_000, REFERENCE_PRICE, REFERENCE_PRICE, false, 60);
        double high = DemandProfile.expectedSellThrough(80, 10_000, REFERENCE_PRICE, REFERENCE_PRICE, false, 60);

        assertThat(high).isGreaterThan(low);
    }

    @Test
    void cheaper_tiers_sell_faster_than_expensive_ones() {
        double cheap = DemandProfile.expectedSellThrough(60, 10_000, 4_000, REFERENCE_PRICE, false, 60);
        double premium = DemandProfile.expectedSellThrough(60, 10_000, 16_000, REFERENCE_PRICE, false, 60);

        assertThat(cheap).isGreaterThan(premium);
    }

    @Test
    void weekend_shows_do_better_than_weeknights() {
        double weekday = DemandProfile.expectedSellThrough(60, 10_000, REFERENCE_PRICE, REFERENCE_PRICE, false, 60);
        double weekend = DemandProfile.expectedSellThrough(60, 10_000, REFERENCE_PRICE, REFERENCE_PRICE, true, 60);

        assertThat(weekend).isGreaterThan(weekday);
    }

    @Test
    @DisplayName("the price factor stays inside its bounds even at absurd prices")
    void price_factor_is_bounded() {
        double free = DemandProfile.expectedSellThrough(60, 10_000, 1, REFERENCE_PRICE, false, 60);
        double outrageous = DemandProfile.expectedSellThrough(60, 10_000, 500_000, REFERENCE_PRICE, false, 60);

        assertThat(free).isPositive();
        assertThat(outrageous).isPositive();
        assertThat(free / outrageous).isLessThanOrEqualTo(1.30 / 0.60 + 0.001);
    }
}
