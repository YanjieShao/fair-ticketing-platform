package com.fairticketing.event.repository;

import com.fairticketing.event.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findByNameAndCityIgnoreCase(String name, String city);
}
