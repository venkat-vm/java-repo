package com.portfolio.dbrefactor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * UNSAFE (expensive) path — returns full entities, triggering eager
     * loads of Partner -> OwnedProperty and RateHistory for every row.
     */
    Optional<Reservation> findByExternalId(String externalId);

    /**
     * FIXED path — Spring Data generates a SQL query selecting ONLY id,
     * externalId, status. No joins to Partner or RateHistory at all.
     */
    Optional<ReservationSummary> findSummaryByExternalId(String externalId);

    /**
     * Bulk version — for the "existence/status check across many records"
     * read path that was the actual production hot path.
     */
    List<ReservationSummary> findByStatus(String status);
}
