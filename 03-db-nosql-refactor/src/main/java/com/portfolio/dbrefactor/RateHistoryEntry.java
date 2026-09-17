package com.portfolio.dbrefactor;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rate_history")
public class RateHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    private BigDecimal previousRate;
    private LocalDateTime changedAt;

    protected RateHistoryEntry() {}

    public RateHistoryEntry(Reservation reservation, BigDecimal previousRate, LocalDateTime changedAt) {
        this.reservation = reservation;
        this.previousRate = previousRate;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public BigDecimal getPreviousRate() { return previousRate; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
