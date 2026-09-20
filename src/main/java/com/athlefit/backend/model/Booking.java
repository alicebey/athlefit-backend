package com.athlefit.backend.model;

import com.athlefit.backend.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours;

    @Column(name = "hourly_rate_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyRateSnapshot;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "payment_deadline")
    private OffsetDateTime paymentDeadline;

    @Column(name = "payment_submitted_at")
    private OffsetDateTime paymentSubmittedAt;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_bank", length = 100)
    private String payerBank;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "payment_confirmed_at")
    private OffsetDateTime paymentConfirmedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private BookingActor cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    protected Booking() {
    }

    public Booking(UserAccount user, Court court, OffsetDateTime startAt, OffsetDateTime endAt,
                   int durationHours, BigDecimal hourlyRateSnapshot, BigDecimal totalPrice,
                   OffsetDateTime paymentDeadline) {
        this.user = user;
        this.court = court;
        this.startAt = startAt;
        this.endAt = endAt;
        this.durationHours = durationHours;
        this.hourlyRateSnapshot = hourlyRateSnapshot;
        this.totalPrice = totalPrice;
        this.paymentDeadline = paymentDeadline;
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isPaymentOverdue(OffsetDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT
                && paymentDeadline != null
                && !now.isBefore(paymentDeadline);
    }

    public void submitPayment(String payerName, String payerBank, String paymentReference,
                              OffsetDateTime now) {
        requireStatus("Payment can only be submitted for an unpaid booking",
                BookingStatus.PENDING_PAYMENT);
        this.payerName = payerName;
        this.payerBank = payerBank;
        this.paymentReference = paymentReference;
        this.paymentSubmittedAt = now;
        this.status = BookingStatus.WAITING_CONFIRMATION;
    }

    public void confirmPayment(OffsetDateTime now) {
        requireStatus("Only unpaid or submitted payments can be confirmed",
                BookingStatus.PENDING_PAYMENT, BookingStatus.WAITING_CONFIRMATION);
        this.paymentConfirmedAt = now;
        this.status = BookingStatus.CONFIRMED;
    }

    public void rejectPayment(String reason, OffsetDateTime now) {
        requireStatus("Only unpaid or submitted payments can be rejected",
                BookingStatus.PENDING_PAYMENT, BookingStatus.WAITING_CONFIRMATION);
        end(BookingStatus.CANCELLED, BookingActor.OWNER, reason, now);
    }

    public void cancelByUser(OffsetDateTime now) {
        if (!status.isActive()) {
            throw new ConflictException("This booking is no longer active");
        }
        end(BookingStatus.CANCELLED, BookingActor.USER, null, now);
    }

    public void expire(OffsetDateTime now) {
        requireStatus("Only unpaid bookings can expire", BookingStatus.PENDING_PAYMENT);
        end(BookingStatus.EXPIRED, BookingActor.SYSTEM, "Payment was not received in time", now);
    }

    /** A user who already transferred money and then cancels must be refunded by the venue. */
    public boolean isRefundRequired() {
        return status == BookingStatus.CANCELLED
                && cancelledBy == BookingActor.USER
                && (paymentSubmittedAt != null || paymentConfirmedAt != null);
    }

    private void end(BookingStatus finalStatus, BookingActor actor, String reason,
                     OffsetDateTime now) {
        this.status = finalStatus;
        this.cancelledBy = actor;
        this.cancellationReason = reason;
        this.cancelledAt = now;
    }

    private void requireStatus(String message, BookingStatus... allowed) {
        for (BookingStatus candidate : allowed) {
            if (candidate == status) {
                return;
            }
        }
        throw new ConflictException(message);
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public Court getCourt() {
        return court;
    }

    public Venue getVenue() {
        return court.getVenueSport().getVenue();
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }

    public int getDurationHours() {
        return durationHours;
    }

    public BigDecimal getHourlyRateSnapshot() {
        return hourlyRateSnapshot;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPaymentDeadline() {
        return paymentDeadline;
    }

    public OffsetDateTime getPaymentSubmittedAt() {
        return paymentSubmittedAt;
    }

    public String getPayerName() {
        return payerName;
    }

    public String getPayerBank() {
        return payerBank;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public OffsetDateTime getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public BookingActor getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
