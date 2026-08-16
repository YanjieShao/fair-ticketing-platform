package com.fairticketing.order.repository;

import com.fairticketing.order.domain.OrderStatus;
import com.fairticketing.order.domain.TicketOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, Long> {

    Optional<TicketOrder> findByOrderNo(String orderNo);

    /** A retried checkout must return the original order rather than create a second one. */
    Optional<TicketOrder> findByIdempotencyKey(String idempotencyKey);

    Optional<TicketOrder> findByActiveLockKey(String activeLockKey);

    @Query("""
            select coalesce(sum(o.quantity), 0)
              from TicketOrder o
             where o.userId = :userId
               and o.tierId = :tierId
               and o.status in :holding
            """)
    int sumOccupyingQuantity(@Param("userId") Long userId,
                             @Param("tierId") Long tierId,
                             @Param("holding") Collection<OrderStatus> holding);

    Page<TicketOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByTierId(Long tierId);

    @Query("""
            select o from TicketOrder o
             where o.status in :statuses
               and o.expiresAt is not null
               and o.expiresAt < :now
            """)
    List<TicketOrder> findExpired(@Param("statuses") Collection<OrderStatus> statuses,
                                  @Param("now") Instant now);
}
