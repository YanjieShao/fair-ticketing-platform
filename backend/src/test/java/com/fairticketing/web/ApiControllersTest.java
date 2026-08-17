package com.fairticketing.web;

import com.fairticketing.ai.domain.AiInsight;
import com.fairticketing.ai.repository.AiInsightRepository;
import com.fairticketing.ai.service.DemandForecastService;
import com.fairticketing.ai.service.InsightService;
import com.fairticketing.ai.service.WaitlistRecommendationService;
import com.fairticketing.ai.web.DemandForecastAdminController;
import com.fairticketing.ai.web.InsightAdminController;
import com.fairticketing.ai.web.InsightResponse;
import com.fairticketing.ai.web.RecommendationResponse;
import com.fairticketing.analytics.DashboardResponse;
import com.fairticketing.analytics.DashboardService;
import com.fairticketing.analytics.web.DashboardAdminController;
import com.fairticketing.auth.service.AuthService;
import com.fairticketing.auth.service.TokenService;
import com.fairticketing.auth.web.AuthController;
import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import com.fairticketing.event.repository.EventRepository;
import com.fairticketing.event.service.EventAdminService;
import com.fairticketing.event.service.EventQueryService;
import com.fairticketing.event.web.CreateEventRequest;
import com.fairticketing.event.web.EventAdminController;
import com.fairticketing.event.web.EventController;
import com.fairticketing.event.web.EventDetailResponse;
import com.fairticketing.event.web.EventSummaryResponse;
import com.fairticketing.inventory.repository.TicketTierRepository;
import com.fairticketing.notification.domain.Notification;
import com.fairticketing.notification.repository.NotificationRepository;
import com.fairticketing.notification.web.NotificationController;
import com.fairticketing.order.domain.TicketOrder;
import com.fairticketing.order.repository.TicketOrderRepository;
import com.fairticketing.order.service.OrderService;
import com.fairticketing.order.web.OrderController;
import com.fairticketing.support.Fixtures;
import com.fairticketing.waitingroom.domain.WaitingRoomStatus;
import com.fairticketing.waitingroom.service.WaitingRoomService;
import com.fairticketing.waitingroom.web.WaitingRoomController;
import com.fairticketing.waitingroom.web.WaitingRoomStreamer;
import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.repository.WaitlistEntryRepository;
import com.fairticketing.waitlist.service.WaitlistService;
import com.fairticketing.waitlist.web.WaitlistController;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiControllersTest {

    private static final Jwt JWT = Fixtures.userJwt(7L);

    @Test
    void auth_controller_maps_register_and_login() {
        AuthService auth = mock(AuthService.class);
        Instant expires = Fixtures.NOW.plusSeconds(60);
        when(auth.register(any(), any(), any())).thenReturn(new TokenService.IssuedToken("t", expires));
        when(auth.login(any(), any())).thenReturn(new TokenService.IssuedToken("t", expires));
        AuthController controller = new AuthController(auth);
        assertThat(controller.register(new AuthController.RegisterRequest("a@b.c", "password1", "Ada")).accessToken())
                .isEqualTo("t");
        assertThat(controller.login(new AuthController.LoginRequest("a@b.c", "password1")).expiresAt())
                .isEqualTo(expires);
    }

    @Test
    void order_controller_round_trips_checkout_pay_cancel_and_lists() {
        OrderService orders = mock(OrderService.class);
        TicketOrderRepository repo = mock(TicketOrderRepository.class);
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        TicketOrder order = TicketOrder.create("FT1", 7L, 5L, 42L, 2, 5000, "key", Fixtures.NOW, Fixtures.NOW.plusSeconds(600));
        when(orders.checkout(7L, 42L, 2, "key")).thenReturn(order);
        when(orders.pay(7L, "FT1")).thenReturn(order);
        when(orders.cancel(7L, "FT1")).thenReturn(order);
        when(orders.findOwned(7L, "FT1")).thenReturn(order);
        when(tiers.showViewsByTierIds(any())).thenReturn(List.of(Fixtures.showView(42L)));
        when(repo.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of(order)));

        OrderController controller = new OrderController(orders, repo, tiers);
        assertThat(controller.checkout(JWT, "key", new OrderController.CheckoutRequest(42L, 2)).orderNo()).isEqualTo("FT1");
        assertThat(controller.pay(JWT, "FT1").tierName()).isEqualTo("Standing");
        assertThat(controller.cancel(JWT, "FT1").city()).isEqualTo("Dublin");
        assertThat(controller.detail(JWT, "FT1").venueTimezone()).isEqualTo("Europe/Dublin");
        assertThat(controller.mine(JWT, 0, 20).getContent()).hasSize(1);
        assertThat(controller.mine(JWT, -1, 999).getContent()).hasSize(1);
    }

    @Test
    void waitlist_controller_joins_lists_and_leaves() {
        WaitlistService waitlist = mock(WaitlistService.class);
        WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
        TicketTierRepository tiers = mock(TicketTierRepository.class);
        WaitlistEntry entry = WaitlistEntry.join(5L, 42L, 7L, 2, 1, Fixtures.NOW);
        when(waitlist.join(7L, 42L, 2)).thenReturn(entry);
        when(waitlist.findOwned(7L, null)).thenReturn(entry);
        when(waitlist.leave(7L, null)).thenReturn(entry);
        when(waitlist.peopleAhead(entry)).thenReturn(3L);
        when(tiers.showViewsByTierIds(any())).thenReturn(List.of(Fixtures.showView(42L)));
        when(entries.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of(entry)));

        WaitlistController controller = new WaitlistController(waitlist, entries, tiers);
        assertThat(controller.join(JWT, new WaitlistController.JoinRequest(42L, 2)).peopleAhead()).isEqualTo(3);
        assertThat(controller.mine(JWT, 0, 20).getContent()).hasSize(1);
        assertThat(controller.detail(JWT, null).eventTitle()).isEqualTo("Live in Dublin");
        assertThat(controller.leave(JWT, null).peopleAhead()).isZero();
    }

    @Test
    void waiting_room_returns_an_open_door_when_the_queue_is_off() {
        WaitingRoomService waitingRoom = mock(WaitingRoomService.class);
        EventRepository events = mock(EventRepository.class);
        WaitingRoomStreamer streamer = mock(WaitingRoomStreamer.class);
        Event event = Fixtures.onSaleEvent(5L);
        when(events.findById(5L)).thenReturn(Optional.of(event));
        when(waitingRoom.enabled()).thenReturn(false);

        WaitingRoomController controller = new WaitingRoomController(waitingRoom, events, streamer);
        assertThat(controller.join(JWT, 5L).status()).isEqualTo(WaitingRoomStatus.ADMITTED);
        assertThat(controller.status(JWT, 5L).position()).isZero();
        when(streamer.open(5L, 7L, false)).thenReturn(new SseEmitter());
        assertThat(controller.stream(JWT, 5L)).isNotNull();
        controller.leave(JWT, 5L);
        verify(waitingRoom).leave(5L, 7L);
    }

    @Test
    void waiting_room_uses_the_queue_when_it_is_on() {
        WaitingRoomService waitingRoom = mock(WaitingRoomService.class);
        EventRepository events = mock(EventRepository.class);
        Event event = Fixtures.onSaleEvent(5L);
        event.setWaitingRoomEnabled(true);
        when(events.findById(5L)).thenReturn(Optional.of(event));
        when(waitingRoom.enabled()).thenReturn(true);
        when(waitingRoom.join(5L, 7L)).thenReturn(
                new WaitingRoomService.Pass(WaitingRoomStatus.WAITING, 4, 10, null, Duration.ofSeconds(30)));
        when(waitingRoom.status(5L, 7L)).thenReturn(
                new WaitingRoomService.Pass(WaitingRoomStatus.ADMITTED, 0, 9, Fixtures.NOW, Duration.ZERO));

        WaitingRoomController controller = new WaitingRoomController(waitingRoom, events, mock(WaitingRoomStreamer.class));
        assertThat(controller.join(JWT, 5L).queueLength()).isEqualTo(10);
        assertThat(controller.status(JWT, 5L).status()).isEqualTo(WaitingRoomStatus.ADMITTED);
    }

    @Test
    void waiting_room_rejects_an_unknown_event() {
        EventRepository events = mock(EventRepository.class);
        when(events.findById(99L)).thenReturn(Optional.empty());
        WaitingRoomController controller = new WaitingRoomController(
                mock(WaitingRoomService.class), events, mock(WaitingRoomStreamer.class));
        assertThatThrownBy(() -> controller.join(JWT, 99L)).isInstanceOf(com.fairticketing.common.error.BusinessException.class);
    }

    @Test
    void event_and_admin_controllers_delegate() {
        EventQueryService queries = mock(EventQueryService.class);
        WaitlistRecommendationService recommendations = mock(WaitlistRecommendationService.class);
        EventAdminService admin = mock(EventAdminService.class);
        EventDetailResponse detail = new EventDetailResponse(
                9L, "Show", "Act", "Pop", "Hall", "Dublin", "Ireland", "Europe/Dublin", "Concert",
                EventStatus.ON_SALE, Fixtures.NOW, Fixtures.NOW, Fixtures.NOW, false, null, null, List.of());
        when(queries.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(queries.detail(9L)).thenReturn(detail);
        when(recommendations.forEvent(9L)).thenReturn(List.of(
                new RecommendationResponse(2L, "Other", "Band", "Rock", "Cork", EventStatus.ON_SALE, 4, 3000, 8, List.of("genre"))));
        when(admin.create(any())).thenReturn(detail);
        when(admin.publish(9L)).thenReturn(detail);
        when(admin.cancelUnsold(9L)).thenReturn(detail);

        EventController events = new EventController(queries, recommendations);
        assertThat(events.search("Dublin", null, null, null, null, null, null, 0, 20)).isEmpty();
        assertThat(events.detail(9L).title()).isEqualTo("Show");
        assertThat(events.recommendations(9L)).hasSize(1);

        EventAdminController adminApi = new EventAdminController(admin);
        CreateEventRequest request = new CreateEventRequest(
                "Show", "Concert", "Act", "Pop", 40, "Hall", "Dublin", "Ireland", 1000, "Europe/Dublin",
                Fixtures.NOW.plus(Duration.ofDays(60)), Fixtures.NOW, Fixtures.NOW.plus(Duration.ofDays(59)), false,
                List.of(new CreateEventRequest.TierRequest("GA", 2500, 100, 4)));
        assertThat(adminApi.create(request).id()).isEqualTo(9L);
        assertThat(adminApi.publish(9L).id()).isEqualTo(9L);
        assertThat(adminApi.cancel(9L).id()).isEqualTo(9L);
    }

    @Test
    void remaining_admin_and_notification_controllers_delegate() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        Notification row = new Notification(7L, "WAITLIST_OFFER", "INFO", "Held", "body", null,
                "ORDER_EVENT", "TEMPLATE", "k", Fixtures.NOW);
        when(notifications.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(new PageImpl<>(List.of(row)));
        assertThat(new NotificationController(notifications).mine(JWT, 0, 20).getContent().getFirst().title())
                .isEqualTo("Held");

        DashboardService dashboard = mock(DashboardService.class);
        when(dashboard.load()).thenReturn(new DashboardResponse(null, List.of(), List.of(), List.of(), List.of(), List.of()));
        assertThat(new DashboardAdminController(dashboard).load()).isNotNull();

        DemandForecastService forecasts = mock(DemandForecastService.class);
        when(forecasts.run()).thenReturn(new DemandForecastService.RunResult(1, 0, "heuristic"));
        assertThat(new DemandForecastAdminController(forecasts).run().predicted()).isEqualTo(1);

        InsightService insights = mock(InsightService.class);
        AiInsightRepository insightRows = mock(AiInsightRepository.class);
        when(insights.run()).thenReturn(new InsightService.RunResult(2));
        when(insightRows.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(
                new AiInsight(AiInsight.SCOPE_EVENT, 5L, "copy", "{}", "TEMPLATE", Fixtures.NOW)));
        InsightAdminController insightApi = new InsightAdminController(insights, insightRows);
        assertThat(insightApi.run().written()).isEqualTo(2);
        List<InsightResponse> latest = insightApi.latest();
        assertThat(latest).hasSize(1);
        assertThat(latest.getFirst().content()).isEqualTo("copy");
    }
}
