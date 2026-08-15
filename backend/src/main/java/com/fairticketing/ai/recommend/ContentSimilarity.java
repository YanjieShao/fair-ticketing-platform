package com.fairticketing.ai.recommend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Cold-start waitlist recommendations: same genre is required, city / category
 * / price / artist only rank the shortlist. Collaborative filtering would need
 * a purchase graph this platform does not have yet.
 */
public final class ContentSimilarity {

    public static final int GENRE = 40;
    public static final int CITY = 25;
    public static final int CATEGORY = 15;
    public static final int PRICE = 15;
    public static final int ARTIST = 10;
    public static final double PRICE_TOLERANCE = 0.30;
    public static final int DEFAULT_LIMIT = 3;

    private ContentSimilarity() {
    }

    public record EventProfile(
            long eventId,
            long artistId,
            String artistName,
            String title,
            String genre,
            String category,
            String city,
            int lowestPriceCents,
            int ticketsAvailable) {
    }

    public record Scored(EventProfile profile, int score, List<String> reasons) {
    }

    public static List<Scored> rank(EventProfile source, List<EventProfile> candidates) {
        return rank(source, candidates, DEFAULT_LIMIT);
    }

    public static List<Scored> rank(EventProfile source, List<EventProfile> candidates, int limit) {
        Objects.requireNonNull(source, "source");
        return candidates.stream()
                .filter(candidate -> candidate.eventId() != source.eventId())
                .filter(candidate -> candidate.ticketsAvailable() > 0)
                .map(candidate -> score(source, candidate))
                .filter(scored -> scored.score() >= GENRE)
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(Comparator.comparingInt((Scored scored) -> scored.profile().ticketsAvailable())
                                .reversed())
                        .thenComparingLong(scored -> scored.profile().eventId()))
                .limit(limit)
                .toList();
    }

    static Scored score(EventProfile source, EventProfile candidate) {
        List<String> reasons = new ArrayList<>();
        int points = 0;

        if (same(source.genre(), candidate.genre())) {
            points += GENRE;
            reasons.add("Same genre (" + candidate.genre() + ")");
        } else {
            return new Scored(candidate, 0, List.of());
        }

        if (same(source.city(), candidate.city())) {
            points += CITY;
            reasons.add("Same city (" + candidate.city() + ")");
        }
        if (same(source.category(), candidate.category())) {
            points += CATEGORY;
            reasons.add("Same category (" + candidate.category() + ")");
        }
        if (similarPrice(source.lowestPriceCents(), candidate.lowestPriceCents())) {
            points += PRICE;
            reasons.add("Similar price");
        }
        if (source.artistId() != 0 && source.artistId() == candidate.artistId()) {
            points += ARTIST;
            reasons.add("Same artist");
        }

        return new Scored(candidate, points, List.copyOf(reasons));
    }

    static boolean similarPrice(int sourceCents, int candidateCents) {
        int baseline = Math.max(sourceCents, 1);
        return Math.abs(sourceCents - candidateCents) <= baseline * PRICE_TOLERANCE;
    }

    private static boolean same(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().toLowerCase(Locale.ROOT).equals(right.trim().toLowerCase(Locale.ROOT));
    }
}
