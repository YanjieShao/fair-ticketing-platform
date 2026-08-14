package com.fairticketing.ai.service;

import com.fairticketing.ai.domain.DemandRiskLevel;
import com.fairticketing.seed.DemandProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Same surface the synthetic generator used to plant history, so a downed
 * Python service still produces numbers that match the training distribution.
 */
@Component
public class HeuristicDemandForecaster implements DemandForecastPort {

    static final String MODEL_VERSION = "heuristic-demand-profile";
    private static final int REFERENCE_PRICE_CENTS = 8_000;

    @Override
    public ForecastBatch forecast(ForecastBatch request) {
        List<Prediction> predictions = new ArrayList<>();
        for (EventFeatures event : request.predict()) {
            predictions.add(predict(event));
        }
        return new ForecastBatch(request.train(), request.predict(), predictions, MODEL_VERSION);
    }

    public Prediction predict(EventFeatures event) {
        double ratio = DemandProfile.expectedSellThrough(
                event.popularityScore(),
                event.venueCapacity(),
                event.avgPriceCents(),
                REFERENCE_PRICE_CENTS,
                event.weekend(),
                event.leadTimeDays());
        int expected = Math.max(0, (int) Math.round(ratio * event.venueCapacity()));
        double demandRatio = event.venueCapacity() <= 0 ? 0.0 : (double) expected / event.venueCapacity();
        return new Prediction(
                event.eventId(),
                expected,
                event.venueCapacity(),
                demandRatio,
                DemandRiskLevel.fromRatio(demandRatio).name(),
                MODEL_VERSION);
    }
}
