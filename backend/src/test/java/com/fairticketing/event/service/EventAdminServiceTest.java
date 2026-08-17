package com.fairticketing.event.service;

import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.event.repository.ArtistRepository;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.repository.VenueRepository;
import com.fairticketing.event.web.CreateEventRequest;
import com.fairticketing.event.web.EventDetailResponse;
import com.fairticketing.inventory.repository.TicketTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    private final ArtistRepository artists = mock(ArtistRepository.class);
    private final VenueRepository venues = mock(VenueRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final TicketTierRepository tiers = mock(TicketTierRepository.class);
    private final EventQueryService queries = mock(EventQueryService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private EventAdminService service;

    @BeforeEach
    void setUp() {
        service = new EventAdminService(artists, venues, events, tiers, queries, clock);
        when(artists.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(venues.findByNameAndCityIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(artists.save(any())).thenAnswer(call -> {
            Artist artist = call.getArgument(0);
            artist.setId(1L);
            return artist;
        });
        when(venues.save(any())).thenAnswer(call -> {
            Venue venue = call.getArgument(0);
            venue.setId(2L);
            return venue;
        });
        when(events.save(any())).thenAnswer(call -> {
            Event event = call.getArgument(0);
            event.setId(9L);
            return event;
        });
        when(queries.detail(9L)).thenReturn(detail(EventStatus.ON_SALE));
    }

    @Test
    void a_show_whose_sales_window_already_opened_goes_on_sale() {
        EventDetailResponse created = service.create(request(
                NOW.minusSeconds(3600), NOW.plus(java.time.Duration.ofDays(59)), NOW.plus(java.time.Duration.ofDays(60))));

        assertThat(created.status()).isEqualTo(EventStatus.ON_SALE);
        verify(tiers).save(any());
    }

    @Test
    void rejects_a_sales_window_that_ends_after_the_show() {
        assertThatThrownBy(() -> service.create(request(
                NOW, NOW.plus(java.time.Duration.ofDays(70)), NOW.plus(java.time.Duration.ofDays(60)))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void only_a_draft_can_be_taken_down() {
        Event draft = new Event();
        draft.setStatus(EventStatus.DRAFT);
        when(events.findById(3L)).thenReturn(Optional.of(draft));
        when(queries.detail(3L)).thenReturn(detail(EventStatus.CANCELLED));

        assertThat(service.cancelUnsold(3L).status()).isEqualTo(EventStatus.CANCELLED);

        Event live = new Event();
        live.setStatus(EventStatus.ON_SALE);
        when(events.findById(4L)).thenReturn(Optional.of(live));
        assertThatThrownBy(() -> service.cancelUnsold(4L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION);
    }

    @Test
    void publish_moves_a_draft_on_sale() {
        Event draft = new Event();
        draft.setStatus(EventStatus.DRAFT);
        when(events.findById(8L)).thenReturn(Optional.of(draft));
        when(queries.detail(8L)).thenReturn(detail(EventStatus.ON_SALE));
        assertThat(service.publish(8L).status()).isEqualTo(EventStatus.ON_SALE);
    }

    @Test
    void publish_rejects_a_missing_show() {
        when(events.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publish(8L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void create_reuses_an_existing_artist_and_venue() {
        when(artists.findByNameIgnoreCase("E2E Act")).thenReturn(Optional.of(new Artist("E2E Act", "Pop", 40)));
        when(venues.findByNameAndCityIgnoreCase("E2E Hall", "Dublin"))
                .thenReturn(Optional.of(new Venue("E2E Hall", "Dublin", "Ireland", 2000, "Europe/Dublin")));

        service.create(request(
                NOW.plus(java.time.Duration.ofDays(1)),
                NOW.plus(java.time.Duration.ofDays(59)),
                NOW.plus(java.time.Duration.ofDays(60))));

        verify(artists, org.mockito.Mockito.never()).save(any());
        verify(venues, org.mockito.Mockito.never()).save(any());
    }

    private static CreateEventRequest request(Instant salesStart, Instant salesEnd, Instant starts) {
        return new CreateEventRequest(
                "E2E Night",
                "Concert",
                "E2E Act",
                "Pop",
                40,
                "E2E Hall",
                "Dublin",
                "Ireland",
                2000,
                "Europe/Dublin",
                starts,
                salesStart,
                salesEnd,
                false,
                List.of(new CreateEventRequest.TierRequest("Standing", 2500, 100, 4)));
    }

    private static EventDetailResponse detail(EventStatus status) {
        return new EventDetailResponse(
                9L, "E2E Night", "E2E Act", "Pop", "E2E Hall", "Dublin", "Ireland",
                "Europe/Dublin", "Concert", status, NOW.plus(java.time.Duration.ofDays(60)),
                NOW.minusSeconds(3600), NOW.plus(java.time.Duration.ofDays(59)), false,
                null, null, List.of());
    }
}
