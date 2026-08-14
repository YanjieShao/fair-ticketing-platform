package com.fairticketing.ai.service;

import com.fairticketing.ai.domain.DemandRiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicDemandForecasterTest {

    private final HeuristicDemandForecaster forecaster = new HeuristicDemandForecaster();

    @Test
    void a_headline_act_in_an_arena_is_high_risk() {
        DemandForecastPort.Prediction prediction = forecaster.predict(features(95, 20_000, 8_000));

        assertThat(prediction.demandRatio()).isGreaterThanOrEqualTo(1.0);
        assertThat(prediction.riskLevel()).isEqualTo(DemandRiskLevel.HIGH.name());
        assertThat(DemandRiskLevel.valueOf(prediction.riskLevel()).shouldOpenWaitingRoom()).isTrue();
    }

    @Test
    void an_unknown_act_does_not_open_the_waiting_room() {
        DemandForecastPort.Prediction prediction = forecaster.predict(features(12, 20_000, 8_000));

        assertThat(prediction.demandRatio()).isLessThan(0.7);
        assertThat(prediction.riskLevel()).isEqualTo(DemandRiskLevel.LOW.name());
    }

    @Test
    void expected_demand_is_the_ratio_applied_to_the_house() {
        DemandForecastPort.Prediction prediction = forecaster.predict(features(50, 10_000, 8_000));

        assertThat(prediction.capacity()).isEqualTo(10_000);
        assertThat(prediction.expectedDemand()).isEqualTo((int) Math.round(prediction.demandRatio() * 10_000));
        assertThat(prediction.modelVersion()).isEqualTo(HeuristicDemandForecaster.MODEL_VERSION);
    }

    private static DemandForecastPort.EventFeatures features(int popularity, int capacity, int price) {
        return new DemandForecastPort.EventFeatures(
                1L, popularity, capacity, price, true, 60, "Pop", "Dublin", "Concert", null);
    }
}
