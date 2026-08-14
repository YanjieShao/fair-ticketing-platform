package com.fairticketing.seed;

/**
 * Turns the features the forecasting model will later be trained on into an
 * expected sell-through ratio. Kept free of framework and randomness so the
 * shape of the curve can be asserted in tests; the generator adds the noise.
 *
 * <p>A ratio above 1.0 means demand outstrips the house, which is exactly the
 * situation the waiting room and the waitlist exist for.
 */
public final class DemandProfile {

    private DemandProfile() {
    }

    public static double expectedSellThrough(int popularityScore,
                                             int capacity,
                                             int priceCents,
                                             int referencePriceCents,
                                             boolean weekend,
                                             int leadTimeDays) {
        double popularity = clamp(popularityScore / 100.0, 0.0, 1.0);

        // Headline acts do not sell a little better, they sell disproportionately better.
        double base = 0.30 + 1.80 * Math.pow(popularity, 1.6);

        // Small rooms sell out easily; arenas have to fill far more seats.
        double sizeFactor = capacity <= 3_000 ? 1.15
                : capacity >= 20_000 ? 0.85
                : 1.0;

        double relativePrice = referencePriceCents <= 0
                ? 1.0
                : (double) priceCents / referencePriceCents;
        double priceFactor = clamp(1.30 - 0.45 * relativePrice, 0.60, 1.30);

        double weekendFactor = weekend ? 1.12 : 1.0;

        // Announcements far ahead of the date lose some urgency.
        double leadTimeFactor = leadTimeDays > 120 ? 0.92 : 1.0;

        return base * sizeFactor * priceFactor * weekendFactor * leadTimeFactor;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
