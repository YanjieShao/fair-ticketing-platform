package com.fairticketing.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SalesSnapshotTest {

    private static final Instant SALES_START = Instant.parse("2026-08-14T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Test
    @DisplayName("sold percent and waitlist pressure are derived, never guessed")
    void derives_sold_percent_and_waitlist_pressure() {
        SalesSnapshot snapshot = SalesSnapshot.from(
                1L, "Night at Croke Park", "Taylor Swift", "ON_SALE",
                SALES_START, NOW, 10_000, 9_200, 200, 1_440, 125_000, "HIGH");

        assertThat(snapshot.hoursOnSale()).isEqualTo(3);
        assertThat(snapshot.remaining()).isEqualTo(800);
        assertThat(snapshot.soldPercent()).isEqualTo(92);
        assertThat(snapshot.waitlistVsRemainingPercent()).isEqualTo(180);
    }

    @Test
    @DisplayName("sold-out leftover of zero does not invent a waitlist ratio")
    void remaining_zero_omits_waitlist_ratio() {
        SalesSnapshot snapshot = SalesSnapshot.from(
                2L, "Sold out", "Act", "SOLD_OUT",
                SALES_START, NOW, 1_000, 1_000, 10, 40, null, null);

        assertThat(snapshot.remaining()).isZero();
        assertThat(snapshot.soldPercent()).isEqualTo(100);
        assertThat(snapshot.waitlistVsRemainingPercent()).isNull();
    }
}
