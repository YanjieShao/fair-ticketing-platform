package com.fairticketing.inventory.repository;

import com.fairticketing.inventory.domain.InventoryLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntry, Long> {

    /** Net movement for a tier, the figure reconciliation compares against. */
    @Query("select coalesce(sum(l.delta), 0) from InventoryLedgerEntry l where l.tierId = :tierId")
    int netDeltaForTier(@Param("tierId") Long tierId);
}
