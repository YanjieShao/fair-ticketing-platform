package com.fairticketing.ai.scheduler;

import com.fairticketing.ai.service.InsightService;
import com.fairticketing.common.config.TicketingProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Insights run after forecasts so the briefing can cite expected demand.
 * Checkout never waits on this job.
 */
@Component
@Order(210)
public class InsightJob implements ApplicationRunner {

    private final InsightService insights;
    private final TicketingProperties properties;

    public InsightJob(InsightService insights, TicketingProperties properties) {
        this.insights = insights;
        this.properties = properties;
    }

    @Scheduled(cron = "${ticketing.llm.cron:0 25 * * * *}")
    public void hourly() {
        insights.run();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.ml().runOnStartup() || properties.llm().runOnStartup()) {
            insights.run();
        }
    }
}
