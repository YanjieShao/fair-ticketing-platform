package com.fairticketing.ai.insight;

import com.fairticketing.analytics.SalesSnapshot;
import com.fairticketing.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmInsightClientTest {

    @Test
    void snapshot_json_escapes_quotes_and_omits_optional_numbers() {
        SalesSnapshot snapshot = new SalesSnapshot(
                7L, "Live \"Night\"", "A\\B", "ON_SALE", 3, 100, 50, 50, 50, 0, 0, null, null, null);
        String json = LlmInsightClient.snapshotJson(snapshot);
        assertThat(json).contains("Live \\\"Night\\\"");
        assertThat(json).contains("A\\\\B");
        assertThat(json).contains("\"waitlistVsRemainingPercent\":null");
        assertThat(json).contains("\"expectedDemand\":null");
        assertThat(json).contains("\"demandRisk\":null");
    }

    @Test
    void blank_api_key_means_the_template_composer_should_run() {
        LlmInsightClient client = new LlmInsightClient(Fixtures.properties());
        assertThat(client.configured()).isFalse();
    }

    @Test
    void a_key_counts_as_configured() {
        var properties = new com.fairticketing.common.config.TicketingProperties(
                Fixtures.properties().inventory(),
                Fixtures.properties().order(),
                Fixtures.properties().waitlist(),
                Fixtures.properties().waitingRoom(),
                Fixtures.properties().payment(),
                Fixtures.properties().security(),
                Fixtures.properties().seed(),
                Fixtures.properties().cors(),
                Fixtures.properties().ml(),
                new com.fairticketing.common.config.TicketingProperties.Llm(
                        "sk-test", "http://127.0.0.1:9", "gpt-4o-mini", Duration.ofSeconds(1), false),
                Fixtures.properties().loadTest(),
                Fixtures.properties().rateLimit());
        assertThat(new LlmInsightClient(properties).configured()).isTrue();
    }

    @Test
    void inner_records_exist_so_jackson_has_somewhere_to_bind() {
        var message = new LlmInsightClient.ChatMessage("user", "hi");
        var request = new LlmInsightClient.ChatRequest("gpt", List.of(message), 0.2);
        var choice = new LlmInsightClient.ChatResponse.Choice(message);
        var response = new LlmInsightClient.ChatResponse(List.of(choice));
        assertThat(request.model()).isEqualTo("gpt");
        assertThat(response.choices().getFirst().message().content()).isEqualTo("hi");
    }
}
