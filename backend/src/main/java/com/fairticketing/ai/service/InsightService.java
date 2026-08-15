package com.fairticketing.ai.service;

import com.fairticketing.ai.domain.AiInsight;
import com.fairticketing.ai.insight.InsightComposer;
import com.fairticketing.ai.insight.LlmInsightClient;
import com.fairticketing.ai.repository.AiInsightRepository;
import com.fairticketing.analytics.SalesSnapshot;
import com.fairticketing.analytics.SalesSnapshotRepository;
import com.fairticketing.auth.domain.UserRole;
import com.fairticketing.auth.repository.UserAccountRepository;
import com.fairticketing.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC);

    private final SalesSnapshotRepository snapshots;
    private final InsightComposer composer;
    private final AiInsightRepository insights;
    private final UserAccountRepository users;
    private final NotificationService notifications;
    private final Clock clock;

    public InsightService(SalesSnapshotRepository snapshots,
                          InsightComposer composer,
                          AiInsightRepository insights,
                          UserAccountRepository users,
                          NotificationService notifications,
                          Clock clock) {
        this.snapshots = snapshots;
        this.composer = composer;
        this.insights = insights;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public RunResult run() {
        Instant now = Instant.now(clock);
        List<SalesSnapshot> live = snapshots.liveEvents();
        int written = 0;
        for (SalesSnapshot snapshot : live) {
            InsightComposer.Composed composed = composer.compose(snapshot);
            insights.save(new AiInsight(
                    AiInsight.SCOPE_EVENT,
                    snapshot.eventId(),
                    composed.content(),
                    LlmInsightClient.snapshotJson(snapshot),
                    composed.generatedBy(),
                    now));
            notifyAdmins(snapshot, composed, now);
            written++;
        }
        log.info("Wrote {} sales insights", written);
        return new RunResult(written);
    }

    private void notifyAdmins(SalesSnapshot snapshot, InsightComposer.Composed composed, Instant now) {
        for (var admin : users.findByRole(UserRole.ADMIN)) {
            String dedupe = "INSIGHT:" + snapshot.eventId() + ":" + HOUR.format(now) + ":" + admin.getId();
            notifications.notifyInsight(
                    admin.getId(),
                    "Sales insight: " + snapshot.title(),
                    composed.content(),
                    LlmInsightClient.snapshotJson(snapshot),
                    composed.generatedBy(),
                    dedupe);
        }
    }

    public record RunResult(int written) {
    }
}
