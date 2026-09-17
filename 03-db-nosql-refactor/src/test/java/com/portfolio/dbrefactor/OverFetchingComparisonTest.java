package com.portfolio.dbrefactor;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the over-fetching fix using Hibernate's own query-count
 * statistics — not a guess, an actual measured query count difference.
 */
@SpringBootTest
@Transactional
class OverFetchingComparisonTest {

    @Autowired
    private ReservationRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        Session session = entityManager.unwrap(Session.class);
        stats = session.getSessionFactory().getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        seedTestData();
    }

    @Test
    void fullEntityFetch_triggersMoreQueriesThanProjection() {
        stats.clear();
        repository.findByExternalId("EXT-001");
        long queriesForFullFetch = stats.getQueryExecutionCount();

        stats.clear();
        repository.findSummaryByExternalId("EXT-002");
        long queriesForProjection = stats.getQueryExecutionCount();

        System.out.println("Queries for FULL entity fetch (Partner + RateHistory eagerly loaded): "
                + queriesForFullFetch);
        System.out.println("Queries for PROJECTION fetch (id, externalId, status only): "
                + queriesForProjection);

        assertTrue(queriesForProjection < queriesForFullFetch,
                "Projection-based fetch should require fewer queries than the full eager-loaded entity graph");
    }

    private void seedTestData() {
        Partner partner = new Partner("Test Partner", "{\"config\":\"large-json-blob\"}");
        entityManager.persist(partner);

        OwnedProperty property = new OwnedProperty(partner, "Test Property");
        entityManager.persist(property);

        Reservation r1 = new Reservation("EXT-001", "CONFIRMED", new BigDecimal("199.99"));
        entityManager.persist(r1);
        entityManager.persist(new RateHistoryEntry(r1, new BigDecimal("179.99"), java.time.LocalDateTime.now()));

        Reservation r2 = new Reservation("EXT-002", "CONFIRMED", new BigDecimal("249.99"));
        entityManager.persist(r2);

        entityManager.flush();
        entityManager.clear(); // force fresh loads from DB, not the persistence context cache
    }
}
