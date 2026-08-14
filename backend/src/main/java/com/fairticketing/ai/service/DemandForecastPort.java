package com.fairticketing.ai.service;

/**
 * The Python model service. Implementations must not be called from checkout.
 */
public interface DemandForecastPort {

    ForecastBatch forecast(ForecastBatch request);

    record EventFeatures(Long eventId,
                         int popularityScore,
                         int venueCapacity,
                         int avgPriceCents,
                         boolean weekend,
                         int leadTimeDays,
                         String genre,
                         String city,
                         String category,
                         Integer soldQuantity) {
    }

    record Prediction(Long eventId,
                      int expectedDemand,
                      int capacity,
                      double demandRatio,
                      String riskLevel,
                      String modelVersion) {
    }

    record ForecastBatch(java.util.List<EventFeatures> train,
                         java.util.List<EventFeatures> predict,
                         java.util.List<Prediction> predictions,
                         String modelVersion) {

        public static ForecastBatch request(java.util.List<EventFeatures> train,
                                            java.util.List<EventFeatures> predict) {
            return new ForecastBatch(train, predict, java.util.List.of(), null);
        }
    }
}
