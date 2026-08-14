package com.fairticketing.ai.repository;

import com.fairticketing.ai.domain.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {

    Optional<DemandForecast> findFirstByEventIdOrderByGeneratedAtDesc(Long eventId);
}
