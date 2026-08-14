package com.fairticketing.ai.service;

import com.fairticketing.ai.repository.DemandFeatureJdbc;
import com.fairticketing.ai.repository.DemandForecastRepository;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemandForecastServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    private final DemandFeatureJdbc features = mock(DemandFeatureJdbc.class);
    private final MlDemandForecastClient ml = mock(MlDemandForecastClient.class);
    private final HeuristicDemandForecaster heuristic = new HeuristicDemandForecaster();
    private final DemandForecastRepository forecasts = mock(DemandForecastRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private DemandForecastService service;

    @BeforeEach
    void setUp() {
        service = new DemandForecastService(
                features, ml, heuristic, forecasts, events, Clock.fixed(NOW, ZoneOffset.UTC));
        when(forecasts.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a HIGH forecast turns the waiting room on, even if Python is down")
    void high_demand_opens_the_waiting_room_via_the_fallback() {
        Event event = upcomingEvent();
        when(features.closedHistory()).thenReturn(List.of());
        when(features.upcoming()).thenReturn(List.of(headliner(event.getId())));
        when(ml.forecast(any())).thenThrow(new ResourceAccessException("connection refused"));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));

        DemandForecastService.RunResult result = service.run();

        assertThat(event.isWaitingRoomEnabled()).isTrue();
        assertThat(result.waitingRoomsOpened()).isEqualTo(1);
        assertThat(result.modelVersion()).isEqualTo(HeuristicDemandForecaster.MODEL_VERSION);
        verify(forecasts).saveAll(anyList());
    }

    @Test
    void a_quiet_show_is_left_on_the_public_sale() {
        Event event = upcomingEvent();
        when(features.closedHistory()).thenReturn(List.of());
        when(features.upcoming()).thenReturn(List.of(unknownAct(event.getId())));
        when(ml.forecast(any())).thenThrow(new ResourceAccessException("connection refused"));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));

        service.run();

        assertThat(event.isWaitingRoomEnabled()).isFalse();
    }

    private static Event upcomingEvent() {
        Event event = new Event();
        event.setId(9L);
        event.setArtist(new Artist("Act", "Pop", 90));
        event.setVenue(new Venue("Arena", "Dublin", "Ireland", 20_000, "Europe/Dublin"));
        event.setTitle("Live");
        event.setCategory("Concert");
        event.setStatus(EventStatus.ON_SALE);
        event.setStartsAt(NOW.plusSeconds(86_400));
        event.setSalesStartAt(NOW.minusSeconds(86_400));
        event.setSalesEndAt(NOW.plusSeconds(80_000));
        event.setWaitingRoomEnabled(false);
        event.setCreatedAt(NOW);
        return event;
    }

    private static DemandForecastPort.EventFeatures headliner(Long eventId) {
        return new DemandForecastPort.EventFeatures(
                eventId, 95, 20_000, 8_000, true, 60, "Pop", "Dublin", "Concert", null);
    }

    private static DemandForecastPort.EventFeatures unknownAct(Long eventId) {
        return new DemandForecastPort.EventFeatures(
                eventId, 12, 20_000, 8_000, false, 60, "Jazz", "Lisbon", "Concert", null);
    }
}
