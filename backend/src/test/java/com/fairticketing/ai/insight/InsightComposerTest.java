package com.fairticketing.ai.insight;

import com.fairticketing.analytics.SalesSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightComposerTest {

    private static final SalesSnapshot SNAPSHOT = SalesSnapshot.from(
            7L, "Show", "Act", "ON_SALE",
            Instant.parse("2026-08-14T09:00:00Z"), Instant.parse("2026-08-14T12:00:00Z"),
            100, 50, 0, 0, null, null);

    private final LlmInsightClient llm = mock(LlmInsightClient.class);
    private final InsightComposer composer = new InsightComposer(llm);

    @Test
    @DisplayName("a missing API key uses the template, so the dashboard is never blank")
    void missing_key_uses_template() {
        when(llm.configured()).thenReturn(false);

        InsightComposer.Composed composed = composer.compose(SNAPSHOT);

        assertThat(composed.generatedBy()).isEqualTo("TEMPLATE");
        assertThat(composed.content()).contains("sold 50% of 100 tickets");
    }

    @Test
    @DisplayName("a fluent LLM paragraph is kept when it cites the snapshot")
    void llm_copy_is_used_when_it_cites_figures() {
        when(llm.configured()).thenReturn(true);
        when(llm.compose(SNAPSHOT)).thenReturn("The show sold 50% of 100 tickets in 3 hours.");

        InsightComposer.Composed composed = composer.compose(SNAPSHOT);

        assertThat(composed.generatedBy()).isEqualTo("LLM");
        assertThat(composed.content()).contains("sold 50% of 100 tickets");
    }

    @Test
    @DisplayName("a down or untrustworthy LLM falls back to the template")
    void llm_failure_falls_back_to_template() {
        when(llm.configured()).thenReturn(true);
        when(llm.compose(SNAPSHOT)).thenThrow(new ResourceAccessException("timeout"));

        InsightComposer.Composed composed = composer.compose(SNAPSHOT);

        assertThat(composed.generatedBy()).isEqualTo("TEMPLATE");
        assertThat(composed.content()).contains("sold 50% of 100 tickets");
    }
}
