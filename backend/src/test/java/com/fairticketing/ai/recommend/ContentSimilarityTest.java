package com.fairticketing.ai.recommend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSimilarityTest {

    private static final ContentSimilarity.EventProfile COLDPLAY = profile(
            1, 10, "Coldplay", "Music of the Spheres", "Pop", "Concert", "Dublin", 8_000, 0);

    @Test
    @DisplayName("a sold-out Pop show recommends other Pop acts ahead of a different genre")
    void ranks_same_genre_then_city_and_price() {
        ContentSimilarity.EventProfile dragons = profile(
                2, 20, "Imagine Dragons", "Mercury", "Pop", "Concert", "Dublin", 7_500, 400);
        ContentSimilarity.EventProfile republic = profile(
                3, 30, "OneRepublic", "Artificial Paradise", "Pop", "Concert", "London", 8_200, 800);
        ContentSimilarity.EventProfile jazz = profile(
                4, 40, "Quiet Cathedral", "After Hours", "Jazz", "Concert", "Dublin", 8_000, 1_200);

        List<ContentSimilarity.Scored> ranked = ContentSimilarity.rank(
                COLDPLAY, List.of(dragons, republic, jazz));

        assertThat(ranked).extracting(scored -> scored.profile().artistName())
                .containsExactly("Imagine Dragons", "OneRepublic");
        assertThat(ranked.getFirst().score()).isGreaterThan(ranked.get(1).score());
        assertThat(ranked.getFirst().reasons()).contains(
                "Same genre (Pop)", "Same city (Dublin)", "Same category (Concert)", "Similar price");
    }

    @Test
    @DisplayName("the sold-out show and anything with no tickets left are dropped")
    void drops_self_and_sold_out_candidates() {
        ContentSimilarity.EventProfile selfStillListed = profile(
                1, 10, "Coldplay", "Music of the Spheres", "Pop", "Concert", "Dublin", 8_000, 50);
        ContentSimilarity.EventProfile soldOutPeer = profile(
                5, 50, "The Weeknd", "After Hours", "Pop", "Concert", "Dublin", 8_000, 0);
        ContentSimilarity.EventProfile open = profile(
                6, 60, "Dua Lipa", "Radical Optimism", "Pop", "Concert", "Dublin", 8_000, 90);

        List<ContentSimilarity.Scored> ranked = ContentSimilarity.rank(
                COLDPLAY, List.of(selfStillListed, soldOutPeer, open));

        assertThat(ranked).extracting(scored -> scored.profile().eventId()).containsExactly(6L);
    }

    @Test
    @DisplayName("price more than 30% away does not get the similar-price bonus")
    void expensive_outlier_loses_price_points() {
        ContentSimilarity.EventProfile vip = profile(
                7, 70, "Imagine Dragons", "Stadium", "Pop", "Concert", "Paris", 20_000, 200);
        ContentSimilarity.Scored scored = ContentSimilarity.score(COLDPLAY, vip);

        assertThat(scored.score()).isEqualTo(ContentSimilarity.GENRE + ContentSimilarity.CATEGORY);
        assertThat(scored.reasons()).doesNotContain("Similar price");
    }

    @Test
    @DisplayName("an empty catalogue of the same genre yields no filler recommendations")
    void no_same_genre_means_empty() {
        ContentSimilarity.EventProfile jazz = profile(
                8, 80, "Quiet Cathedral", "After Hours", "Jazz", "Concert", "Dublin", 8_000, 400);

        assertThat(ContentSimilarity.rank(COLDPLAY, List.of(jazz))).isEmpty();
    }

    private static ContentSimilarity.EventProfile profile(long eventId,
                                                          long artistId,
                                                          String artist,
                                                          String title,
                                                          String genre,
                                                          String category,
                                                          String city,
                                                          int price,
                                                          int tickets) {
        return new ContentSimilarity.EventProfile(
                eventId, artistId, artist, title, genre, category, city, price, tickets);
    }
}
