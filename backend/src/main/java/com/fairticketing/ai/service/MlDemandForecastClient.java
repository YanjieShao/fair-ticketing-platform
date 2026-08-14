package com.fairticketing.ai.service;

import com.fairticketing.common.config.TicketingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Talks to the Python service. Any failure, including a 422 for too little
 * history, is the caller's cue to use {@link HeuristicDemandForecaster}.
 */
@Component
public class MlDemandForecastClient {

    private static final Logger log = LoggerFactory.getLogger(MlDemandForecastClient.class);

    private final RestClient http;

    public MlDemandForecastClient(TicketingProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.ml().timeout());
        factory.setReadTimeout(properties.ml().timeout());
        this.http = RestClient.builder()
                .baseUrl(properties.ml().baseUrl())
                .requestFactory(factory)
                .build();
    }

    public DemandForecastPort.ForecastBatch forecast(DemandForecastPort.ForecastBatch request) {
        MlRequest body = new MlRequest(
                request.train().stream().map(MlTrainRow::from).toList(),
                request.predict().stream().map(MlPredictRow::from).toList());
        try {
            MlResponse response = http.post()
                    .uri("/forecast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MlResponse.class);
            if (response == null) {
                throw new IllegalStateException("ML service returned an empty body");
            }
            return new DemandForecastPort.ForecastBatch(
                    request.train(),
                    request.predict(),
                    response.predictions().stream()
                            .map(row -> new DemandForecastPort.Prediction(
                                    row.eventId(),
                                    row.expectedDemand(),
                                    row.capacity(),
                                    row.demandRatio(),
                                    row.riskLevel(),
                                    row.modelVersion()))
                            .toList(),
                    response.modelVersion());
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("Demand model at {} did not answer: {}", http, ex.getMessage());
            throw ex;
        }
    }

    record MlRequest(List<MlTrainRow> train, List<MlPredictRow> predict) {
    }

    record MlTrainRow(int popularityScore,
                      int venueCapacity,
                      int avgPriceCents,
                      boolean weekend,
                      int leadTimeDays,
                      String genre,
                      String city,
                      String category,
                      int soldQuantity) {
        static MlTrainRow from(DemandForecastPort.EventFeatures row) {
            return new MlTrainRow(
                    row.popularityScore(), row.venueCapacity(), row.avgPriceCents(),
                    row.weekend(), row.leadTimeDays(), row.genre(), row.city(),
                    row.category(), row.soldQuantity() == null ? 0 : row.soldQuantity());
        }
    }

    record MlPredictRow(long eventId,
                        int popularityScore,
                        int venueCapacity,
                        int avgPriceCents,
                        boolean weekend,
                        int leadTimeDays,
                        String genre,
                        String city,
                        String category) {
        static MlPredictRow from(DemandForecastPort.EventFeatures row) {
            return new MlPredictRow(
                    row.eventId(), row.popularityScore(), row.venueCapacity(),
                    row.avgPriceCents(), row.weekend(), row.leadTimeDays(),
                    row.genre(), row.city(), row.category());
        }
    }

    record MlResponse(String modelVersion, List<MlPrediction> predictions) {
    }

    record MlPrediction(long eventId,
                        int expectedDemand,
                        int capacity,
                        double demandRatio,
                        String riskLevel,
                        String modelVersion) {
    }
}
