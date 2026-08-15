package com.fairticketing.loadtest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/load-test")
@ConditionalOnProperty(prefix = "ticketing.load-test", name = "enabled", havingValue = "true")
public class LoadTestFixtureController {

    private final LoadTestFixtureService fixtures;

    public LoadTestFixtureController(LoadTestFixtureService fixtures) {
        this.fixtures = fixtures;
    }

    @PostMapping("/fixtures")
    public LoadTestFixtureService.Fixture create(@Valid @RequestBody FixtureRequest request) {
        return fixtures.create(request.buyers(), request.stock());
    }

    @GetMapping("/result/{tierId}")
    public LoadTestFixtureService.Result result(@PathVariable Long tierId) {
        return fixtures.result(tierId);
    }

    public record FixtureRequest(
            @Min(1) @Max(20_000) int buyers,
            @Min(1) @Max(100_000) int stock) {
    }
}
