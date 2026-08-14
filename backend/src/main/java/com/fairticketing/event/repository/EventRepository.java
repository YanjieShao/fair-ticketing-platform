package com.fairticketing.event.repository;

import com.fairticketing.event.domain.Event;
import com.fairticketing.event.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    /**
     * Availability for a whole page of events in one query, so the listing does
     * not fan out into a query per event.
     *
     * @return rows of [eventId, ticketsAvailable, lowestPriceCents]
     */
    @Query("""
            select t.event.id,
                   sum(t.totalQuantity - t.reservedQuantity),
                   min(t.priceCents)
              from TicketTier t
             where t.event.id in :eventIds
             group by t.event.id
            """)
    List<Object[]> availabilityByEvent(@Param("eventIds") Collection<Long> eventIds);

    List<Event> findByStatus(EventStatus status);

    @Query("""
            select e from Event e
            where e.status = :status
              and e.salesStartAt <= :now
              and e.salesEndAt > :now
            """)
    List<Event> findSellingNow(@Param("status") EventStatus status, @Param("now") Instant now);
}
