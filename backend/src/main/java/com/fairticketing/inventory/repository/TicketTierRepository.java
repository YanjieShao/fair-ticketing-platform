package com.fairticketing.inventory.repository;

import com.fairticketing.inventory.domain.TicketTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {

    List<TicketTier> findByEventId(Long eventId);

    @Query("""
            select new com.fairticketing.inventory.repository.TierPurchaseView(
                       t.id, e.id, t.priceCents, t.maxPerUser,
                       e.status, e.salesStartAt, e.salesEndAt)
              from TicketTier t
              join t.event e
             where t.id = :id
            """)
    Optional<TierPurchaseView> findPurchaseView(@Param("id") Long id);

    /** Used by the database-locking reserver. Serialises buyers on one tier. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketTier t where t.id = :id")
    Optional<TicketTier> findByIdForUpdate(@Param("id") Long id);

    /**
     * Conditional update used by the lock-free path: the WHERE clause is what
     * makes overselling impossible, so a zero return means "sold out", not an error.
     */
    @Modifying
    @Query("""
            update TicketTier t
               set t.reservedQuantity = t.reservedQuantity + :quantity
             where t.id = :id
               and t.totalQuantity - t.reservedQuantity >= :quantity
            """)
    int tryReserve(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("""
            update TicketTier t
               set t.reservedQuantity = t.reservedQuantity - :quantity
             where t.id = :id
               and t.reservedQuantity >= :quantity
            """)
    int release(@Param("id") Long id, @Param("quantity") int quantity);
}
