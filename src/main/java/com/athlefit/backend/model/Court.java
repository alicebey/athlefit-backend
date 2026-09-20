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

import java.util.UUID;

@Entity
@Table(name = "venue_courts")
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_sport_id", nullable = false)
    private VenueSport venueSport;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected Court() {
    }

    public Court(VenueSport venueSport, String name) {
        this.venueSport = venueSport;
        this.name = name;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public VenueSport getVenueSport() {
        return venueSport;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
