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
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false)
    private int capacity;

    /** IANA zone id. Timestamps are stored in UTC and rendered in this zone. */
    @Column(nullable = false, length = 64)
    private String timezone;

    public Venue(String name, String city, String country, int capacity, String timezone) {
        this.name = name;
        this.city = city;
        this.country = country;
        this.capacity = capacity;
        this.timezone = timezone;
    }
}
