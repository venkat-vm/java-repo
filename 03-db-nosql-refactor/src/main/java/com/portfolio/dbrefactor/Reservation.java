package com.portfolio.dbrefactor;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * The ORIGINAL, full entity — represents the over-fetching problem.
 *
 * Many read paths (e.g. "does this reservation exist and what's its status?")
 * only need 1-2 columns, but Hibernate's default fetch behavior on
 * associations (and any accidentally-EAGER relationships) pulls the ENTIRE
 * object graph — including deeply nested and circular associations —
 * driving unnecessary DB load on high-volume read paths.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    private String status;

    private BigDecimal totalAmount;

    // Deliberately EAGER to reproduce the real over-fetching problem —
    // every load of a Reservation also loads its full Partner graph.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    // Also EAGER — a full history of every rate change, often unnecessary
    // for simple existence/status checks.
    @OneToMany(mappedBy = "reservation", fetch = FetchType.EAGER)
    private List<RateHistoryEntry> rateHistory;

    protected Reservation() {
        // JPA requires a no-arg constructor
    }

    public Reservation(String externalId, String status, BigDecimal totalAmount) {
        this.externalId = externalId;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Partner getPartner() { return partner; }
    public List<RateHistoryEntry> getRateHistory() { return rateHistory; }
}
