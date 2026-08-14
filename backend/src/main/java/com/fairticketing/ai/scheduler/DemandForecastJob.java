package com.fairticketing.ai.scheduler;

import com.fairticketing.ai.service.DemandForecastService;
import com.fairticketing.common.config.TicketingProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Forecasts sit behind a schedule because they must never share a thread with
 * checkout. {@code Order} 200 lets the seeder finish first when both run at boot.
 */
@Component
@Order(200)
public class DemandForecastJob implements ApplicationRunner {

    private final DemandForecastService forecasts;
    private final TicketingProperties properties;

    public DemandForecastJob(DemandForecastService forecasts, TicketingProperties properties) {
        this.forecasts = forecasts;
        this.properties = properties;
    }

    @Scheduled(cron = "${ticketing.ml.cron:0 20 * * * *}")
    public void hourly() {
        forecasts.run();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.ml().runOnStartup()) {
            forecasts.run();
        }
    }
}
