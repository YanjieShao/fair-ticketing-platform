package com.fairticketing.ai.web;

import com.fairticketing.ai.service.DemandForecastService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/forecasts")
public class DemandForecastAdminController {

    private final DemandForecastService forecasts;

    public DemandForecastAdminController(DemandForecastService forecasts) {
        this.forecasts = forecasts;
    }

    @PostMapping("/run")
    public DemandForecastService.RunResult run() {
        return forecasts.run();
    }
}
