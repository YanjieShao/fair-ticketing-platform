package com.fairticketing.ai.web;

import com.fairticketing.ai.repository.AiInsightRepository;
import com.fairticketing.ai.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/insights")
public class InsightAdminController {

    private final InsightService insights;
    private final AiInsightRepository repository;

    public InsightAdminController(InsightService insights, AiInsightRepository repository) {
        this.insights = insights;
        this.repository = repository;
    }

    @GetMapping
    public List<InsightResponse> latest() {
        return repository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(InsightResponse::from)
                .toList();
    }

    @PostMapping("/run")
    public InsightService.RunResult run() {
        return insights.run();
    }
}
