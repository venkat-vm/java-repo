package com.portfolio.dbrefactor;

/**
 * THE FIX — a lightweight, read-only projection.
 *
 * For read paths that only need existence/status (e.g. "is this reservation
 * still valid?"), this interface tells Spring Data JPA to generate a SQL
 * query selecting ONLY these columns — no Partner, no RateHistory, no
 * OwnedProperty graph. Hibernate never even constructs the full Reservation
 * entity for these calls.
 *
 * Production note: the real system used Hibernate .hbm.xml mapping files to
 * define lite entities, since the codebase's persistence layer already used
 * XML-based mappings elsewhere — this demo uses Spring Data's
 * interface-based projections instead, which is the modern, annotation-based
 * equivalent of the same underlying idea: define a narrower read view scoped
 * to exactly what a given use case needs, instead of always materializing
 * the full entity graph.
 */
public interface ReservationSummary {
    Long getId();
    String getExternalId();
    String getStatus();
}
