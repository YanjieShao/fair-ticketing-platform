package com.fairticketing.waitlist.repository;

import com.fairticketing.waitlist.domain.WaitlistEntry;
import com.fairticketing.waitlist.domain.WaitlistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByActiveLockKey(String activeLockKey);

    Optional<WaitlistEntry> findByUserIdAndTierIdAndStatus(Long userId, Long tierId, WaitlistStatus status);

    Page<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("select coalesce(max(e.positionSeq), 0) from WaitlistEntry e where e.tierId = :tierId")
    long maxPositionSeq(@Param("tierId") Long tierId);

    long countByTierIdAndStatusAndPositionSeqLessThan(Long tierId, WaitlistStatus status, long positionSeq);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WaitlistEntry> findFirstByTierIdAndStatusOrderByPositionSeqAsc(Long tierId, WaitlistStatus status);

    @Query("""
            select e from WaitlistEntry e
             where e.status = com.fairticketing.waitlist.domain.WaitlistStatus.OFFERED
               and e.offerExpiresAt is not null
               and e.offerExpiresAt < :now
            """)
    List<WaitlistEntry> findExpiredOffers(@Param("now") Instant now);
}
