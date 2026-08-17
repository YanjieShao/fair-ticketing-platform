package com.fairticketing.scheduler;

import com.fairticketing.ai.scheduler.DemandForecastJob;
import com.fairticketing.ai.scheduler.InsightJob;
import com.fairticketing.ai.service.DemandForecastService;
import com.fairticketing.ai.service.InsightService;
import com.fairticketing.common.config.TicketingProperties;
import com.fairticketing.order.scheduler.OrderExpiryJob;
import com.fairticketing.order.service.OrderService;
import com.fairticketing.support.Fixtures;
import com.fairticketing.waitlist.scheduler.WaitlistOfferExpiryJob;
import com.fairticketing.waitlist.service.WaitlistService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobsTest {

    @Test
    void expiry_jobs_call_the_matching_service() {
        OrderService orders = mock(OrderService.class);
        new OrderExpiryJob(orders).releaseExpiredOrders();
        verify(orders).expireOverdueOrders();

        WaitlistService waitlist = mock(WaitlistService.class);
        new WaitlistOfferExpiryJob(waitlist).expireOffers();
        verify(waitlist).expireOverdueOffers();
    }

    @Test
    void forecast_and_insight_jobs_honour_the_startup_flags() {
        DemandForecastService forecasts = mock(DemandForecastService.class);
        InsightService insights = mock(InsightService.class);
        TicketingProperties off = Fixtures.properties();
        new DemandForecastJob(forecasts, off).run(new DefaultApplicationArguments());
        new InsightJob(insights, off).run(new DefaultApplicationArguments());
        verify(forecasts, never()).run();
        verify(insights, never()).run();

        new DemandForecastJob(forecasts, off).hourly();
        new InsightJob(insights, off).hourly();
        verify(forecasts).run();
        verify(insights).run();
    }

    @Test
    void insight_job_also_runs_at_boot_when_forecasts_do() {
        InsightService insights = mock(InsightService.class);
        TicketingProperties properties = mock(TicketingProperties.class);
        TicketingProperties.Ml ml = new TicketingProperties.Ml("http://x", java.time.Duration.ofSeconds(1), true);
        TicketingProperties.Llm llm = new TicketingProperties.Llm("", "http://x", "m", java.time.Duration.ofSeconds(1), false);
        when(properties.ml()).thenReturn(ml);
        when(properties.llm()).thenReturn(llm);
        new InsightJob(insights, properties).run(new DefaultApplicationArguments());
        verify(insights).run();
    }
}
