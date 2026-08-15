package com.fairticketing.ai.insight;

import com.fairticketing.analytics.SalesSnapshot;
import com.fairticketing.common.config.TicketingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Asks an LLM to phrase a snapshot. The prompt forbids new numbers; we still
 * drop the reply if it does not cite the computed sold percent and capacity.
 */
@Component
public class LlmInsightClient {

    private static final Logger log = LoggerFactory.getLogger(LlmInsightClient.class);

    static final String SYSTEM_PROMPT = """
            You write a two-sentence briefing for a ticketing operator.
            Use ONLY the integers in the JSON. Do not invent, round into a new
            figure, estimate, or add facts that are not present. Mention the
            sold percent, hours on sale, and waitlist pressure when those
            fields are present. Do not use markdown. Do not use bullet points.
            """;

    private final TicketingProperties properties;
    private final RestClient http;

    public LlmInsightClient(TicketingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.llm().timeout());
        factory.setReadTimeout(properties.llm().timeout());
        this.http = RestClient.builder()
                .baseUrl(properties.llm().baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.llm().apiKey())
                .build();
    }

    public boolean configured() {
        String key = properties.llm().apiKey();
        return key != null && !key.isBlank();
    }

    public String compose(SalesSnapshot snapshot) {
        ChatResponse response = http.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChatRequest(
                        properties.llm().model(),
                        List.of(
                                new ChatMessage("system", SYSTEM_PROMPT),
                                new ChatMessage("user", snapshotJson(snapshot))),
                        0.2))
                .retrieve()
                .body(ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null) {
            throw new IllegalStateException("LLM returned no choices");
        }
        String content = response.choices().getFirst().message().content();
        if (!TemplateInsightComposer.citesComputedFigures(content, snapshot)) {
            log.warn("Discarding LLM copy that did not cite snapshot figures for event {}", snapshot.eventId());
            throw new IllegalStateException("LLM copy did not cite computed figures");
        }
        return content.trim();
    }

    public static String snapshotJson(SalesSnapshot snapshot) {
        String waitlistVs = snapshot.waitlistVsRemainingPercent() == null
                ? "null"
                : String.valueOf(snapshot.waitlistVsRemainingPercent());
        String expected = snapshot.expectedDemand() == null ? "null" : String.valueOf(snapshot.expectedDemand());
        String risk = snapshot.demandRisk() == null ? "null" : "\"" + snapshot.demandRisk() + "\"";
        return """
                {"eventId":%d,"title":"%s","artistName":"%s","status":"%s","hoursOnSale":%d,\
                "capacity":%d,"reserved":%d,"remaining":%d,"soldPercent":%d,\
                "waitlistPeople":%d,"waitlistTickets":%d,"waitlistVsRemainingPercent":%s,\
                "expectedDemand":%s,"demandRisk":%s}
                """.formatted(
                snapshot.eventId(),
                escape(snapshot.title()),
                escape(snapshot.artistName()),
                escape(snapshot.status()),
                snapshot.hoursOnSale(),
                snapshot.capacity(),
                snapshot.reserved(),
                snapshot.remaining(),
                snapshot.soldPercent(),
                snapshot.waitlistPeople(),
                snapshot.waitlistTickets(),
                waitlistVs,
                expected,
                risk);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record ChatRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    record ChatMessage(String role, String content) {
    }

    record ChatResponse(List<Choice> choices) {
        record Choice(ChatMessage message) {
        }
    }
}
