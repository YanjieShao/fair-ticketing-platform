package com.fairticketing.ai.service;

import com.fairticketing.ai.domain.DemandForecast;
import com.fairticketing.ai.domain.DemandRiskLevel;
import com.fairticketing.ai.repository.DemandFeatureJdbc;
import com.fairticketing.ai.repository.DemandForecastRepository;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline batch: ask the model (or the heuristic) how oversubscribed upcoming
 * shows will be, persist the numbers, and open the waiting room for HIGH ones.
 */
@Service
public class DemandForecastService {

    private static final Logger log = LoggerFactory.getLogger(DemandForecastService.class);

    private final DemandFeatureJdbc features;
    private final MlDemandForecastClient ml;
    private final HeuristicDemandForecaster heuristic;
    private final DemandForecastRepository forecasts;
    private final EventRepository events;
    private final Clock clock;

    public DemandForecastService(DemandFeatureJdbc features,
                                 MlDemandForecastClient ml,
                                 HeuristicDemandForecaster heuristic,
                                 DemandForecastRepository forecasts,
                                 EventRepository events,
                                 Clock clock) {
        this.features = features;
        this.ml = ml;
        this.heuristic = heuristic;
        this.forecasts = forecasts;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public RunResult run() {
        List<DemandForecastPort.EventFeatures> train = features.closedHistory();
        List<DemandForecastPort.EventFeatures> predict = features.upcoming();
        if (predict.isEmpty()) {
            return new RunResult(0, 0, HeuristicDemandForecaster.MODEL_VERSION);
        }

        DemandForecastPort.ForecastBatch batch = DemandForecastPort.ForecastBatch.request(train, predict);
        String source;
        try {
            batch = ml.forecast(batch);
            source = batch.modelVersion();
        } catch (RestClientException | IllegalStateException ex) {
            batch = heuristic.forecast(batch);
            source = batch.modelVersion();
            log.warn("Falling back to {} after the model service failed", source);
        }

        Instant now = Instant.now(clock);
        int opened = 0;
        List<DemandForecast> rows = new ArrayList<>();
        for (DemandForecastPort.Prediction prediction : batch.predictions()) {
            DemandRiskLevel level = DemandRiskLevel.valueOf(prediction.riskLevel());
            rows.add(new DemandForecast(
                    prediction.eventId(),
                    prediction.expectedDemand(),
                    prediction.capacity(),
                    BigDecimal.valueOf(prediction.demandRatio()).setScale(3, RoundingMode.HALF_UP),
                    level,
                    prediction.modelVersion(),
                    now));
            if (level.shouldOpenWaitingRoom()) {
                opened += openWaitingRoom(prediction.eventId());
            }
        }
        forecasts.saveAll(rows);
        log.info("Wrote {} demand forecasts via {}, opened {} waiting rooms", rows.size(), source, opened);
        return new RunResult(rows.size(), opened, source);
    }

    private int openWaitingRoom(Long eventId) {
        Event event = events.findById(eventId).orElse(null);
        if (event == null || event.isWaitingRoomEnabled()) {
            return 0;
        }
        event.setWaitingRoomEnabled(true);
        return 1;
    }

    public record RunResult(int predicted, int waitingRoomsOpened, String modelVersion) {
    }
}
