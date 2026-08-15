package com.fairticketing.ai.insight;

import com.fairticketing.analytics.SalesSnapshot;

/**
 * Turns a {@link SalesSnapshot} into operator copy without inventing figures.
 * The LLM path must cite the same integers; otherwise we keep this text.
 */
public final class TemplateInsightComposer {

    private TemplateInsightComposer() {
    }

    public static String compose(SalesSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append(snapshot.title())
                .append(" (")
                .append(snapshot.artistName())
                .append(") sold ")
                .append(snapshot.soldPercent())
                .append("% of ")
                .append(snapshot.capacity())
                .append(" tickets in ")
                .append(snapshot.hoursOnSale())
                .append(" hours (")
                .append(snapshot.reserved())
                .append(" reserved, ")
                .append(snapshot.remaining())
                .append(" left).");

        if (snapshot.waitlistTickets() > 0 && snapshot.waitlistVsRemainingPercent() != null) {
            text.append(" The waitlist wants ")
                    .append(snapshot.waitlistTickets())
                    .append(" tickets, ")
                    .append(snapshot.waitlistVsRemainingPercent())
                    .append("% of remaining stock.");
        } else if (snapshot.waitlistTickets() > 0) {
            text.append(" The waitlist wants ")
                    .append(snapshot.waitlistTickets())
                    .append(" tickets and nothing is left on public sale.");
        }

        if (snapshot.expectedDemand() != null && snapshot.demandRisk() != null) {
            text.append(" Forecast is ")
                    .append(snapshot.demandRisk())
                    .append(" at ")
                    .append(snapshot.expectedDemand())
                    .append(" expected demand.");
        }
        return text.toString();
    }

    /**
     * Rejects model output that drifted away from the snapshot, so a fluent
     * paragraph cannot smuggle in a number we did not compute.
     */
    public static boolean citesComputedFigures(String text, SalesSnapshot snapshot) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains(snapshot.soldPercent() + "%")
                && text.contains(String.valueOf(snapshot.capacity()));
    }
}
