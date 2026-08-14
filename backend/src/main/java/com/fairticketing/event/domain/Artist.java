package com.fairticketing.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "artists")
@Getter
@Setter
@NoArgsConstructor
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String genre;

    /** 0-100. A demand forecasting feature, not something buyers ever see. */
    @Column(name = "popularity_score", nullable = false)
    private int popularityScore;

    public Artist(String name, String genre, int popularityScore) {
        this.name = name;
        this.genre = genre;
        this.popularityScore = popularityScore;
    }
}
