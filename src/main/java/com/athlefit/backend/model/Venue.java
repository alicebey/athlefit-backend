package com.athlefit.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private String name;

    @Column
    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "image_url")
    private String imageUrl;

    private String phone;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "venue_open_days", joinColumns = @JoinColumn(name = "venue_id"))
    @Column(name = "day_name", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> openDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VenueSource source;

    @Column(name = "provider_place_id", unique = true)
    private String providerPlaceId;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserAccount owner;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_account_holder")
    private String bankAccountHolder;

    protected Venue() {
    }

    public static Venue geoapify(
            String providerPlaceId,
            String name,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            Set<DayOfWeek> openDays,
            LocalTime openTime,
            LocalTime closeTime
    ) {
        Venue venue = new Venue();
        venue.source = VenueSource.GEOAPIFY;
        venue.providerPlaceId = providerPlaceId;
        venue.name = name;
        venue.address = address;
        venue.latitude = latitude;
        venue.longitude = longitude;
        venue.phone = phone;
        venue.openDays = new HashSet<>(openDays);
        venue.openTime = openTime;
        venue.closeTime = closeTime;
        venue.active = true;
        return venue;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPhone() {
        return phone;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public Set<DayOfWeek> getOpenDays() {
        return openDays;
    }

    public VenueSource getSource() {
        return source;
    }

    public String getProviderPlaceId() {
        return providerPlaceId;
    }

    public boolean isActive() {
        return active;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public String getBankAccountHolder() {
        return bankAccountHolder;
    }

    public boolean hasPaymentAccount() {
        return bankName != null && bankAccountNumber != null && bankAccountHolder != null;
    }

    public boolean isOwnedBy(String firebaseUid) {
        return owner != null && owner.getFirebaseUid().equals(firebaseUid);
    }

    public void assignOwner(UserAccount owner) {
        this.owner = owner;
    }

    public void updatePhone(String phone) {
        this.phone = phone;
    }

    public void updateSchedule(Set<DayOfWeek> openDays, LocalTime openTime, LocalTime closeTime) {
        this.openDays.clear();
        this.openDays.addAll(openDays);
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }

    public void updatePaymentAccount(String bankName, String bankAccountNumber,
                                     String bankAccountHolder) {
        this.bankName = bankName;
        this.bankAccountNumber = bankAccountNumber;
        this.bankAccountHolder = bankAccountHolder;
    }
}
