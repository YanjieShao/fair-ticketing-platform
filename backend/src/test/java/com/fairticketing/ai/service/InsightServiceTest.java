package com.fairticketing.ai.service;

import com.fairticketing.ai.domain.AiInsight;
import com.fairticketing.ai.insight.InsightComposer;
import com.fairticketing.ai.repository.AiInsightRepository;
import com.fairticketing.analytics.SalesSnapshot;
import com.fairticketing.analytics.SalesSnapshotRepository;
import com.fairticketing.auth.domain.UserAccount;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.repository.UserAccountRepository;
import com.fairticketing.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsightServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    private final SalesSnapshotRepository snapshots = mock(SalesSnapshotRepository.class);
    private final InsightComposer composer = mock(InsightComposer.class);
    private final AiInsightRepository insights = mock(AiInsightRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private InsightService service;

    @BeforeEach
    void setUp() {
        service = new InsightService(
                snapshots, composer, insights, users, notifications, Clock.fixed(NOW, ZoneOffset.UTC));
        when(insights.save(any(AiInsight.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("persists the snapshot next to the copy and notifies admins once per hour")
    void writes_insight_and_notifies_admins() {
        SalesSnapshot snapshot = SalesSnapshot.from(
                9L, "Night at Croke Park", "Taylor Swift", "ON_SALE",
                NOW.minusSeconds(3 * 3600), NOW, 10_000, 9_200, 200, 1_440, 125_000, "HIGH");
        when(snapshots.liveEvents()).thenReturn(List.of(snapshot));
        when(composer.compose(snapshot)).thenReturn(new InsightComposer.Composed(
                "Reached 92% of 10000 tickets in 3 hours.", "TEMPLATE"));
        UserAccount admin = new UserAccount(
                "admin@fairticketing.local", "hash", "Admin", UserRole.ADMIN, NOW);
        admin.setId(42L);
        when(users.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));

        InsightService.RunResult result = service.run();

        assertThat(result.written()).isEqualTo(1);
        ArgumentCaptor<AiInsight> saved = ArgumentCaptor.forClass(AiInsight.class);
        verify(insights).save(saved.capture());
        assertThat(saved.getValue().getScopeType()).isEqualTo(AiInsight.SCOPE_EVENT);
        assertThat(saved.getValue().getScopeId()).isEqualTo(9L);
        assertThat(saved.getValue().getGeneratedBy()).isEqualTo("TEMPLATE");
        assertThat(saved.getValue().getPayloadJson()).contains("\"soldPercent\":92");
        verify(notifications).notifyInsight(
                eq(42L),
                eq("Sales insight: Night at Croke Park"),
                eq("Reached 92% of 10000 tickets in 3 hours."),
                org.mockito.ArgumentMatchers.contains("\"soldPercent\":92"),
                eq("TEMPLATE"),
                eq("INSIGHT:9:2026081412:42"));
    }
}
