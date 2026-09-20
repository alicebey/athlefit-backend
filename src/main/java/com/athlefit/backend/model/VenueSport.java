package com.athlefit.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "venue_sports")
public class VenueSport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    protected VenueSport() {
    }

    public VenueSport(Venue venue, Sport sport, BigDecimal hourlyRate) {
        this.venue = venue;
        this.sport = sport;
        this.hourlyRate = hourlyRate;
    }

    public UUID getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public Sport getSport() {
        return sport;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void updateHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }
}
