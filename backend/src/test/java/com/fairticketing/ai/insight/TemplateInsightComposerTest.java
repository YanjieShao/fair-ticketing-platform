package com.fairticketing.ai.insight;

import com.fairticketing.analytics.SalesSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateInsightComposerTest {

    private static final SalesSnapshot SNAPSHOT = SalesSnapshot.from(
            1L,
            "Night at Croke Park",
            "Taylor Swift",
            "ON_SALE",
            Instant.parse("2026-08-14T09:00:00Z"),
            Instant.parse("2026-08-14T12:00:00Z"),
            10_000,
            9_200,
            200,
            1_440,
            125_000,
            "HIGH");

    @Test
    @DisplayName("template copy interpolates the computed 92% / 3h / 180% figures")
    void interpolates_computed_figures() {
        String text = TemplateInsightComposer.compose(SNAPSHOT);

        assertThat(text).contains("Night at Croke Park");
        assertThat(text).contains("sold 92% of 10000 tickets in 3 hours");
        assertThat(text).contains("The waitlist wants 1440 tickets, 180% of remaining stock.");
        assertThat(text).contains("Forecast is HIGH at 125000 expected demand.");
    }

    @Test
    @DisplayName("LLM copy is rejected unless it cites the snapshot integers")
    void rejects_copy_that_invents_or_omits_figures() {
        assertThat(TemplateInsightComposer.citesComputedFigures(
                "Reached 92% of 10000 tickets; waitlist is 180% of remaining stock.", SNAPSHOT))
                .isTrue();
        assertThat(TemplateInsightComposer.citesComputedFigures(
                "Demand looks extremely high and the waitlist is huge.", SNAPSHOT))
                .isFalse();
        assertThat(TemplateInsightComposer.citesComputedFigures("sold 99% of 10000 tickets", SNAPSHOT))
                .isFalse();
    }
}
