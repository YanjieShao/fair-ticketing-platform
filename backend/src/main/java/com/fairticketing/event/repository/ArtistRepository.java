package com.fairticketing.event.repository;

import com.fairticketing.event.domain.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    Optional<Artist> findByNameIgnoreCase(String name);
}
