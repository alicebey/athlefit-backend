package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.CreateBookingRequest;
import com.athlefit.backend.dto.request.SubmitPaymentRequest;
import com.athlefit.backend.dto.response.BookingResponse;
import com.athlefit.backend.exception.ConflictException;
import com.athlefit.backend.exception.NotFoundException;
import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.BookingActor;
import com.athlefit.backend.model.BookingStatus;
import com.athlefit.backend.model.Court;
import com.athlefit.backend.model.UserAccount;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.model.VenueTime;
import com.athlefit.backend.repository.BookingRepository;
import com.athlefit.backend.repository.CourtRepository;
import com.athlefit.backend.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Customer-facing booking rules.
 *
 * <p>Lifecycle: a new booking is PENDING_PAYMENT and holds its court for {@link #PAYMENT_WINDOW}.
 * The customer transfers money and submits the transfer details (WAITING_CONFIRMATION); the venue
 * owner then confirms (CONFIRMED) or rejects it (CANCELLED). Unpaid bookings EXPIRE automatically.
 * Paid or submitted bookings can be cancelled by the customer until {@link #CANCELLATION_CUTOFF}
 * before the start time; unpaid ones can be cancelled any time before they start.
 */
@Service
public class BookingService {

    public static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);
    public static final Duration CANCELLATION_CUTOFF = Duration.ofHours(2);
    private static final String BOOKING_CONFLICT_MESSAGE =
            "Court is already booked for the selected time";

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final UserService userService;
    private final VenueService venueService;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            CourtRepository courtRepository,
            UserService userService,
            VenueService venueService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.courtRepository = courtRepository;
        this.userService = userService;
        this.venueService = venueService;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(AuthenticatedUser identity, CreateBookingRequest request) {
        OffsetDateTime now = now();
        expireOverduePayments(now);
        UserAccount user = userService.getOrCreate(identity);
        VenueSport venueSport = venueService.requireVenueSport(
                request.venueId(),
                request.sportSlug()
        );
        OffsetDateTime startAt = request.startAt().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime endAt = startAt.plusHours(request.durationHours());

        validateSchedule(venueSport, startAt, endAt, now);
        List<Court> availableCourts = courtRepository.findAvailable(
                venueSport.getId(),
                BookingStatus.ACTIVE,
                startAt,
                endAt,
                PageRequest.of(0, 1)
        );
        if (availableCourts.isEmpty()) {
            throw new ConflictException("All courts are booked for the selected time");
        }

        BigDecimal totalPrice = venueSport.getHourlyRate()
                .multiply(BigDecimal.valueOf(request.durationHours()));
        OffsetDateTime paymentDeadline = earliest(now.plus(PAYMENT_WINDOW), startAt);
        Booking booking = new Booking(
                user,
                availableCourts.get(0),
                startAt,
                endAt,
                request.durationHours(),
                venueSport.getHourlyRate(),
                totalPrice,
                paymentDeadline
        );
        try {
            Booking savedBooking = bookingRepository.saveAndFlush(booking);
            return toResponse(savedBooking, now);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(BOOKING_CONFLICT_MESSAGE);
        }
    }

    @Transactional
    public List<BookingResponse> findMyBookings(AuthenticatedUser identity) {
        OffsetDateTime now = now();
        expireOverduePayments(now);
        UserAccount user = userService.getOrCreate(identity);
        return bookingRepository.findAllByUserIdOrderByStartAtDesc(user.getId()).stream()
                .map(booking -> toResponse(booking, now))
                .toList();
    }

    @Transactional
    public BookingResponse findMyBooking(AuthenticatedUser identity, UUID bookingId) {
        OffsetDateTime now = now();
        expireOverduePayments(now);
        UserAccount user = userService.getOrCreate(identity);
        return toResponse(requireOwnedBooking(bookingId, user), now);
    }

    @Transactional
    public BookingResponse submitPayment(
            AuthenticatedUser identity,
            UUID bookingId,
            SubmitPaymentRequest request
    ) {
        OffsetDateTime now = now();
        UserAccount user = userService.getOrCreate(identity);
        Booking booking = requireOwnedBooking(bookingId, user);
        if (booking.isPaymentOverdue(now)) {
            // The scheduled expiry job records the EXPIRED status; this request only reports it.
            throw new ConflictException(
                    "The payment window has closed and this booking has expired"
            );
        }
        booking.submitPayment(
                request.payerName().trim(),
                request.payerBank().trim(),
                normalizeOptional(request.paymentReference()),
                now
        );
        return toResponse(booking, now);
    }

    @Transactional
    public BookingResponse cancel(AuthenticatedUser identity, UUID bookingId) {
        OffsetDateTime now = now();
        UserAccount user = userService.getOrCreate(identity);
        Booking booking = requireOwnedBooking(bookingId, user);
        if (!booking.getStatus().isActive()) {
            throw new ConflictException("This booking is no longer active");
        }
        if (!isCancellable(booking, now)) {
            throw new ConflictException(
                    "Paid bookings can only be cancelled at least 2 hours before the start time"
            );
        }
        booking.cancelByUser(now);
        return toResponse(booking, now);
    }

    /** Marks unpaid bookings past their deadline as EXPIRED so their courts become free. */
    @Transactional
    public int expireOverduePayments() {
        return expireOverduePayments(now());
    }

    public BookingResponse toResponse(Booking booking, OffsetDateTime now) {
        return BookingResponse.from(
                booking,
                venueService.toResponse(booking.getCourt().getVenueSport()),
                isCancellable(booking, now),
                cancellableUntil(booking)
        );
    }

    private int expireOverduePayments(OffsetDateTime now) {
        return bookingRepository.expireOverduePayments(
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.EXPIRED,
                BookingActor.SYSTEM,
                now
        );
    }

    private boolean isCancellable(Booking booking, OffsetDateTime now) {
        if (!booking.getStatus().isActive() || !now.isBefore(booking.getStartAt())) {
            return false;
        }
        return booking.getStatus() == BookingStatus.PENDING_PAYMENT
                || !now.isAfter(booking.getStartAt().minus(CANCELLATION_CUTOFF));
    }

    private OffsetDateTime cancellableUntil(Booking booking) {
        if (!booking.getStatus().isActive()) {
            return null;
        }
        return booking.getStatus() == BookingStatus.PENDING_PAYMENT
                ? booking.getStartAt()
                : booking.getStartAt().minus(CANCELLATION_CUTOFF);
    }

    private Booking requireOwnedBooking(UUID bookingId, UserAccount user) {
        return bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new NotFoundException("Booking was not found"));
    }

    private void validateSchedule(
            VenueSport venueSport,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            OffsetDateTime now
    ) {
        if (!startAt.isAfter(now)) {
            throw new IllegalArgumentException("Booking time must be in the future");
        }
        if (startAt.isAfter(now.plusDays(AvailabilityService.MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException(
                    "Bookings can be made up to " + AvailabilityService.MAX_DAYS_AHEAD
                            + " days ahead"
            );
        }

        ZonedDateTime localStart = startAt.atZoneSameInstant(VenueTime.ZONE);
        ZonedDateTime localEnd = endAt.atZoneSameInstant(VenueTime.ZONE);
        venueService.validateOperatingHours(venueSport.getVenue(), localStart, localEnd);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    private static OffsetDateTime earliest(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
