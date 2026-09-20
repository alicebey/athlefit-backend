package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.AssignOwnerRequest;
import com.athlefit.backend.dto.request.RejectPaymentRequest;
import com.athlefit.backend.dto.request.UpdateVenueSettingsRequest;
import com.athlefit.backend.dto.request.UpsertVenueSportRequest;
import com.athlefit.backend.exception.ConflictException;
import com.athlefit.backend.exception.ForbiddenException;
import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.BookingActor;
import com.athlefit.backend.model.BookingStatus;
import com.athlefit.backend.model.Court;
import com.athlefit.backend.model.Sport;
import com.athlefit.backend.model.UserAccount;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.repository.BookingRepository;
import com.athlefit.backend.repository.CourtRepository;
import com.athlefit.backend.repository.SportRepository;
import com.athlefit.backend.repository.UserAccountRepository;
import com.athlefit.backend.repository.VenueRepository;
import com.athlefit.backend.repository.VenueSportRepository;
import com.athlefit.backend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerVenueServiceTest {

    private static final UUID VENUE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_SPORT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BOOKING_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-30T00:00:00Z");
    private static final AuthenticatedUser OWNER =
            new AuthenticatedUser("owner-uid", "owner@example.com", "Owner");
    private static final AuthenticatedUser STRANGER =
            new AuthenticatedUser("stranger-uid", "stranger@example.com", "Stranger");
    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser("admin-uid", "admin@example.com", "Admin");

    private VenueRepository venueRepository;
    private VenueSportRepository venueSportRepository;
    private CourtRepository courtRepository;
    private BookingRepository bookingRepository;
    private UserAccountRepository userAccountRepository;
    private OwnerVenueService service;
    private Venue venue;
    private VenueSport venueSport;

    @BeforeEach
    void setUp() {
        venueRepository = mock(VenueRepository.class);
        venueSportRepository = mock(VenueSportRepository.class);
        SportRepository sportRepository = mock(SportRepository.class);
        courtRepository = mock(CourtRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        AdminVenueService adminVenueService = mock(AdminVenueService.class);
        BookingService bookingService = mock(BookingService.class);

        UserAccount owner = mock(UserAccount.class);
        when(owner.getFirebaseUid()).thenReturn(OWNER.uid());
        venue = Venue.geoapify(
                "place-1", "Smash Arena", "Jakarta",
                new BigDecimal("-6.2"), new BigDecimal("106.8"), null,
                Set.of(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(22, 0)
        );
        venue.assignOwner(owner);
        Sport sport = mock(Sport.class);
        when(sport.getSlug()).thenReturn("badminton");
        when(sport.getName()).thenReturn("Badminton");
        venueSport = mock(VenueSport.class);
        when(venueSport.getId()).thenReturn(VENUE_SPORT_ID);
        when(venueSport.getVenue()).thenReturn(venue);
        when(venueSport.getSport()).thenReturn(sport);

        when(venueRepository.findWithOwnerById(VENUE_ID)).thenReturn(Optional.of(venue));
        when(venueSportRepository.findByVenueIdAndSportSlug(VENUE_ID, "badminton"))
                .thenReturn(Optional.of(venueSport));
        when(venueSportRepository.findAllByVenueIdOrderBySportName(any()))
                .thenReturn(List.of(venueSport));
        when(adminVenueService.isAdmin(ADMIN)).thenReturn(true);

        service = new OwnerVenueService(
                venueRepository,
                venueSportRepository,
                sportRepository,
                courtRepository,
                bookingRepository,
                userAccountRepository,
                adminVenueService,
                bookingService,
                Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void strangersCannotManageAVenue() {
        assertThrows(ForbiddenException.class,
                () -> service.findManagedVenue(STRANGER, VENUE_ID));
    }

    @Test
    void ownerUpdatesScheduleAndPaymentAccount() {
        service.updateSettings(OWNER, VENUE_ID, new UpdateVenueSettingsRequest(
                "0812", Set.of(DayOfWeek.SATURDAY), LocalTime.of(9, 0), LocalTime.of(21, 0),
                false, "BCA", "123", "Smash Arena"
        ));

        assertEquals(Set.of(DayOfWeek.SATURDAY), venue.getOpenDays());
        assertEquals(LocalTime.of(21, 0), venue.getCloseTime());
        assertEquals("123", venue.getBankAccountNumber());
        assertFalse(venue.isActive());
    }

    @Test
    void rejectsIncompletePaymentAccount() {
        assertThrows(IllegalArgumentException.class, () -> service.updateSettings(
                OWNER, VENUE_ID,
                new UpdateVenueSettingsRequest(null, null, null, null, null, "BCA", null, null)
        ));
    }

    @Test
    void growingCourtCountCreatesNumberedCourts() {
        Court existing = new Court(venueSport, "Court 1");
        when(courtRepository.findAllByVenueSportId(VENUE_SPORT_ID))
                .thenReturn(new ArrayList<>(List.of(existing)));

        service.upsertSport(OWNER, VENUE_ID, "badminton",
                new UpsertVenueSportRequest(new BigDecimal("80000.00"), 3));

        verify(venueSport).updateHourlyRate(new BigDecimal("80000.00"));
        verify(courtRepository).saveAll(argThat(courts -> {
            List<String> names = StreamSupport.stream(courts.spliterator(), false)
                    .map(Court::getName)
                    .toList();
            return names.equals(List.of("Court 2", "Court 3"));
        }));
    }

    @Test
    void cannotRemoveACourtThatHasUpcomingBookings() {
        Court first = new Court(venueSport, "Court 1");
        Court second = new Court(venueSport, "Court 2");
        when(courtRepository.findAllByVenueSportId(VENUE_SPORT_ID))
                .thenReturn(new ArrayList<>(List.of(first, second)));
        when(bookingRepository.existsByCourtIdAndStatusInAndEndAtAfter(any(), any(), eq(NOW)))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.upsertSport(
                OWNER, VENUE_ID, "badminton",
                new UpsertVenueSportRequest(new BigDecimal("80000.00"), 1)
        ));
    }

    @Test
    void ownerConfirmsAndRejectsSubmittedPayments() {
        Booking booking = submittedBooking();

        service.confirmPayment(OWNER, BOOKING_ID);

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(NOW, booking.getPaymentConfirmedAt());

        Booking another = submittedBooking();
        service.rejectPayment(OWNER, BOOKING_ID, new RejectPaymentRequest(" No transfer found "));

        assertEquals(BookingStatus.CANCELLED, another.getStatus());
        assertEquals(BookingActor.OWNER, another.getCancelledBy());
        assertEquals("No transfer found", another.getCancellationReason());
    }

    @Test
    void strangersCannotConfirmPayments() {
        submittedBooking();

        assertThrows(ForbiddenException.class, () -> service.confirmPayment(STRANGER, BOOKING_ID));
    }

    @Test
    void onlyAdminsAssignOwners() {
        assertThrows(ForbiddenException.class, () -> service.assignOwner(
                OWNER, VENUE_ID, new AssignOwnerRequest("new@example.com")
        ));

        service.assignOwner(ADMIN, VENUE_ID, new AssignOwnerRequest(" "));

        assertNull(venue.getOwner());
    }

    private Booking submittedBooking() {
        Court court = new Court(venueSport, "Court 1");
        Booking booking = new Booking(
                mock(UserAccount.class), court,
                NOW.plusDays(1), NOW.plusDays(1).plusHours(1), 1,
                new BigDecimal("60000.00"), new BigDecimal("60000.00"),
                NOW.plusMinutes(30)
        );
        booking.submitPayment("Eki", "BCA", null, NOW);
        when(bookingRepository.findWithDetailsById(BOOKING_ID)).thenReturn(Optional.of(booking));
        return booking;
    }
}
