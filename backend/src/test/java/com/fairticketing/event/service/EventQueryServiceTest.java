package com.fairticketing.event.service;

import com.fairticketing.ai.domain.AiInsight;
import com.fairticketing.ai.domain.DemandForecast;
import com.fairticketing.ai.domain.DemandRiskLevel;
import com.fairticketing.ai.repository.AiInsightRepository;
import com.fairticketing.ai.repository.DemandForecastRepository;
import com.fairticketing.common.error.BusinessException;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.web.EventSearchCriteria;
import com.fairticketing.inventory.domain.TicketTier;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.inventory.service.InventoryService;
import com.fairticketing.support.Fixtures;
import com.fairticketing.waitingroom.service.WaitingRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventQueryServiceTest {

    private final EventRepository events = mock(EventRepository.class);
    private final TicketTierRepository tiers = mock(TicketTierRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final DemandForecastRepository forecasts = mock(DemandForecastRepository.class);
    private final AiInsightRepository insights = mock(AiInsightRepository.class);
    private final WaitingRoomService waitingRoom = mock(WaitingRoomService.class);
    private EventQueryService service;

    @BeforeEach
    void setUp() {
        service = new EventQueryService(events, tiers, inventory, forecasts, insights, waitingRoom);
        when(waitingRoom.enabled()).thenReturn(false);
    }

    @Test
    void search_maps_availability_onto_each_row() {
        Event event = Fixtures.onSaleEvent(5L);
        when(events.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(events.availabilityByEvent(List.of(5L))).thenReturn(List.<Object[]>of(new Object[]{5L, 12, 2500}));

        var page = service.search(new EventSearchCriteria("Dublin", "Harbour", "Concert",
                Fixtures.NOW, Fixtures.NOW.plusSeconds(10), 1000, 9000), PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().ticketsAvailable()).isEqualTo(12);
        assertThat(page.getContent().getFirst().lowestPriceCents()).isEqualTo(2500);
        assertThat(page.getContent().getFirst().city()).isEqualTo("Dublin");
    }

    @Test
    void search_on_an_empty_page_does_not_query_availability() {
        when(events.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.search(new EventSearchCriteria(null, null, null, null, null, null, null),
                PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void detail_uses_the_live_remaining_count_and_optional_ai_views() {
        Event event = Fixtures.onSaleEvent(5L);
        TicketTier tier = Fixtures.standing(event, 42L);
        when(events.findById(5L)).thenReturn(Optional.of(event));
        when(tiers.findByEventIdOrderByPriceCentsAsc(5L)).thenReturn(List.of(tier));
        when(inventory.remaining(tier)).thenReturn(7);
        when(forecasts.findFirstByEventIdOrderByGeneratedAtDesc(5L)).thenReturn(Optional.of(
                new DemandForecast(5L, 20_000, 13_000, new BigDecimal("1.500"), DemandRiskLevel.HIGH,
                        "xgb-1", Fixtures.NOW)));
        when(insights.findFirstByScopeTypeAndScopeIdOrderByCreatedAtDesc(eq(AiInsight.SCOPE_EVENT), eq(5L)))
                .thenReturn(Optional.of(new AiInsight(AiInsight.SCOPE_EVENT, 5L, "Sold 80%.", "{}", "TEMPLATE", Fixtures.NOW)));

        var detail = service.detail(5L);
        assertThat(detail.tiers().getFirst().availableQuantity()).isEqualTo(7);
        assertThat(detail.forecast().riskLevel()).isEqualTo("HIGH");
        assertThat(detail.insight().content()).isEqualTo("Sold 80%.");
    }

    @Test
    void detail_rejects_an_unknown_show() {
        when(events.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(99L)).isInstanceOf(BusinessException.class);
    }
}
