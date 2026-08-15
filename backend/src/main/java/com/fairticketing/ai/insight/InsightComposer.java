package com.fairticketing.ai.insight;

import com.fairticketing.analytics.SalesSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prefers the LLM when a key is configured, then falls back to the template
 * so a missing API never leaves the dashboard blank.
 */
@Component
public class InsightComposer {

    private static final Logger log = LoggerFactory.getLogger(InsightComposer.class);

    private final LlmInsightClient llm;

    public InsightComposer(LlmInsightClient llm) {
        this.llm = llm;
    }

    public Composed compose(SalesSnapshot snapshot) {
        if (llm.configured()) {
            try {
                return new Composed(llm.compose(snapshot), "LLM");
            } catch (RuntimeException ex) {
                log.warn("LLM insight failed for event {}, using template: {}", snapshot.eventId(), ex.getMessage());
            }
        }
        return new Composed(TemplateInsightComposer.compose(snapshot), "TEMPLATE");
    }

    public record Composed(String content, String generatedBy) {
    }
}
