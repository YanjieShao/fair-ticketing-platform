package com.fairticketing.analytics.web;

import com.fairticketing.analytics.DashboardResponse;
import com.fairticketing.analytics.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardAdminController {

    private final DashboardService dashboard;

    public DashboardAdminController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public DashboardResponse load() {
        return dashboard.load();
    }
}
