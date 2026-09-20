package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.CreateBookingRequest;
import com.athlefit.backend.dto.request.SubmitPaymentRequest;
import com.athlefit.backend.dto.response.BookingResponse;
import com.athlefit.backend.dto.response.VenueResponse;
import com.athlefit.backend.exception.ConflictException;
import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.BookingActor;
import com.athlefit.backend.model.BookingStatus;
import com.athlefit.backend.model.Court;
import com.athlefit.backend.model.UserAccount;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.repository.BookingRepository;
import com.athlefit.backend.repository.CourtRepository;
import com.athlefit.backend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private static final UUID VENUE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_SPORT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BOOKING_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-30T00:00:00Z");
    private static final AuthenticatedUser IDENTITY =
            new AuthenticatedUser("firebase-user", "user@example.com", "Athlefit User");

    private BookingRepository bookingRepository;
    private CourtRepository courtRepository;
    private VenueService venueService;
    private BookingService bookingService;
    private UserAccount user;
    private Venue venue;
    private VenueSport venueSport;
    private Court court;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        courtRepository = mock(CourtRepository.class);
        UserService userService = mock(UserService.class);
        venueService = mock(VenueService.class);
        user = mock(UserAccount.class);
        venue = mock(Venue.class);
        venueSport = mock(VenueSport.class);
        court = mock(Court.class);
        bookingService = new BookingService(
                bookingRepository,
                courtRepository,
                userService,
                venueService,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC)
        );

        when(userService.getOrCreate(IDENTITY)).thenReturn(user);
        when(venueService.requireVenueSport(VENUE_ID, "badminton")).thenReturn(venueSport);
        when(venueSport.getId()).thenReturn(VENUE_SPORT_ID);
        when(venueSport.getVenue()).thenReturn(venue);
        when(venueSport.getHourlyRate()).thenReturn(new BigDecimal("60000.00"));
        when(court.getVenueSport()).thenReturn(venueSport);
        when(court.getName()).thenReturn("Court 2");
        when(courtRepository.findAvailable(
                any(), any(), any(), any(), any(Pageable.class)
        )).thenReturn(List.of(court));
        when(venueService.toResponse(venueSport)).thenReturn(mock(VenueResponse.class));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsUnpaidBookingOnAvailableCourtAndCalculatesPriceOnServer() {
        BookingResponse result = bookingService.create(IDENTITY, request(2));

        assertEquals(new BigDecimal("120000.00"), result.totalPrice());
        assertEquals(2, result.durationHours());
        assertEquals(BookingStatus.PENDING_PAYMENT, result.status());
        assertEquals("Court 2", result.courtName());
        assertEquals(NOW.plusMinutes(30).toInstant(), result.paymentDeadline().toInstant());
        assertEquals("+07:00", result.startAt().getOffset().getId());
        verify(bookingRepository).expireOverduePayments(
                BookingStatus.PENDING_PAYMENT,
                BookingStatus.EXPIRED,
                BookingActor.SYSTEM,
                NOW
        );
    }

    @Test
    void paymentDeadlineNeverExceedsTheStartTime() {
        CreateBookingRequest soon = new CreateBookingRequest(
                VENUE_ID,
                "badminton",
                NOW.plusMinutes(10),
                1
        );

        BookingResponse result = bookingService.create(IDENTITY, soon);

        assertEquals(NOW.plusMinutes(10).toInstant(), result.paymentDeadline().toInstant());
    }

    @Test
    void rejectsBookingWhenEveryCourtIsOccupied() {
        when(courtRepository.findAvailable(
                any(), any(), any(), any(), any(Pageable.class)
        )).thenReturn(List.of());

        assertThrows(ConflictException.class, () -> bookingService.create(IDENTITY, request(1)));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsBookingOutsideOperatingHours() {
        doThrow(new IllegalArgumentException("Venue is closed"))
                .when(venueService)
                .validateOperatingHours(any(), any(), any());

        assertThrows(IllegalArgumentException.class, () -> bookingService.create(
                IDENTITY,
                request(2)
        ));
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsBookingTooFarAhead() {
        CreateBookingRequest farAway = new CreateBookingRequest(
                VENUE_ID,
                "badminton",
                NOW.plusDays(AvailabilityService.MAX_DAYS_AHEAD + 1),
                1
        );

        assertThrows(IllegalArgumentException.class, () -> bookingService.create(IDENTITY, farAway));
    }

    @Test
    void submittingPaymentMovesBookingToWaitingConfirmation() {
        Booking booking = ownedBooking(NOW.plusDays(1), NOW.plusMinutes(30));

        BookingResponse result = bookingService.submitPayment(
                IDENTITY,
                BOOKING_ID,
                new SubmitPaymentRequest(" Eki ", "BCA", " TRX-1 ")
        );

        assertEquals(BookingStatus.WAITING_CONFIRMATION, result.status());
        assertEquals("Eki", booking.getPayerName());
        assertEquals("TRX-1", booking.getPaymentReference());
    }

    @Test
    void rejectsPaymentSubmittedAfterTheDeadline() {
        Booking booking = ownedBooking(NOW.plusDays(1), NOW.minusMinutes(1));

        assertThrows(ConflictException.class, () -> bookingService.submitPayment(
                IDENTITY,
                BOOKING_ID,
                new SubmitPaymentRequest("Eki", "BCA", null)
        ));
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
    }

    @Test
    void paidBookingCannotBeCancelledWithinTwoHoursOfStart() {
        Booking booking = ownedBooking(NOW.plusMinutes(90), NOW.plusMinutes(30));
        booking.confirmPayment(NOW);

        assertThrows(ConflictException.class, () -> bookingService.cancel(IDENTITY, BOOKING_ID));
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
    }

    @Test
    void paidBookingCanBeCancelledEarlyAndIsFlaggedForRefund() {
        Booking booking = ownedBooking(NOW.plusHours(5), NOW.plusMinutes(30));
        booking.submitPayment("Eki", "BCA", null, NOW);

        BookingResponse result = bookingService.cancel(IDENTITY, BOOKING_ID);

        assertEquals(BookingStatus.CANCELLED, result.status());
        assertEquals(BookingActor.USER, result.cancelledBy());
        assertTrue(result.refundRequired());
        assertFalse(result.cancellable());
    }

    @Test
    void unpaidBookingCanBeCancelledShortlyBeforeStart() {
        ownedBooking(NOW.plusMinutes(30), NOW.plusMinutes(30));

        BookingResponse result = bookingService.cancel(IDENTITY, BOOKING_ID);

        assertEquals(BookingStatus.CANCELLED, result.status());
        assertFalse(result.refundRequired());
    }

    private Booking ownedBooking(OffsetDateTime startAt, OffsetDateTime paymentDeadline) {
        Booking booking = new Booking(
                user,
                court,
                startAt,
                startAt.plusHours(1),
                1,
                new BigDecimal("60000.00"),
                new BigDecimal("60000.00"),
                paymentDeadline
        );
        when(bookingRepository.findByIdAndUserId(any(), any())).thenReturn(Optional.of(booking));
        return booking;
    }

    private CreateBookingRequest request(int durationHours) {
        return new CreateBookingRequest(
                VENUE_ID,
                "badminton",
                OffsetDateTime.parse("2026-09-01T10:00:00+07:00"),
                durationHours
        );
    }
}
