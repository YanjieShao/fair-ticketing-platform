package com.fairticketing.ai.repository;

import com.fairticketing.ai.domain.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    Optional<AiInsight> findFirstByScopeTypeAndScopeIdOrderByCreatedAtDesc(String scopeType, Long scopeId);

    List<AiInsight> findTop50ByOrderByCreatedAtDesc();
}
