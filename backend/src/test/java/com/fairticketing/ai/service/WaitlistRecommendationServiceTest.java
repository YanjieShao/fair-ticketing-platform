package com.fairticketing.ai.service;

import com.fairticketing.common.error.BusinessException;
import com.fairticketing.common.error.ErrorCode;
import com.fairticketing.event.domain.Artist;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.domain.Venue;
import com.fairticketing.event.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitlistRecommendationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    private final EventRepository events = mock(EventRepository.class);
    private WaitlistRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new WaitlistRecommendationService(events);
    }

    @Test
    @DisplayName("returns on-sale Pop shows for a sold-out Pop event, in score order")
    void recommends_same_genre_still_on_sale() {
        Event coldplay = event(1L, artist(10L, "Coldplay", "Pop"), "Dublin", EventStatus.SOLD_OUT);
        Event dragons = event(2L, artist(20L, "Imagine Dragons", "Pop"), "Dublin", EventStatus.ON_SALE);
        Event jazz = event(3L, artist(30L, "Quiet Cathedral", "Jazz"), "Dublin", EventStatus.ON_SALE);

        when(events.findById(1L)).thenReturn(Optional.of(coldplay));
        when(events.findOnSaleOtherThan(EventStatus.ON_SALE, 1L)).thenReturn(List.of(dragons, jazz));
        when(events.availabilityByEvent(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(
                new Object[]{1L, 0, 8_000},
                new Object[]{2L, 400, 7_500},
                new Object[]{3L, 900, 8_000}));

        var recs = service.forEvent(1L);

        assertThat(recs).extracting(rec -> rec.artistName()).containsExactly("Imagine Dragons");
        assertThat(recs.getFirst().reasons()).contains("Same genre (Pop)", "Same city (Dublin)");
        assertThat(recs.getFirst().ticketsAvailable()).isEqualTo(400);
    }

    @Test
    @DisplayName("a missing event is a 404, not an empty list")
    void missing_event_is_not_found() {
        when(events.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forEvent(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static Artist artist(long id, String name, String genre) {
        Artist artist = new Artist(name, genre, 80);
        artist.setId(id);
        return artist;
    }

    private static Event event(long id, Artist artist, String city, EventStatus status) {
        Event event = new Event();
        event.setId(id);
        event.setArtist(artist);
        event.setVenue(new Venue(city + " Arena", city, "Ireland", 10_000, "Europe/Dublin"));
        event.setTitle(artist.getName() + " Live");
        event.setCategory("Concert");
        event.setStatus(status);
        event.setStartsAt(NOW.plusSeconds(86_400));
        event.setSalesStartAt(NOW.minusSeconds(3_600));
        event.setSalesEndAt(NOW.plusSeconds(80_000));
        event.setCreatedAt(NOW);
        return event;
    }
}
