package com.athlefit.backend.service;

import com.athlefit.backend.dto.response.AvailabilityResponse;
import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.Court;
import com.athlefit.backend.model.Sport;
import com.athlefit.backend.model.UserAccount;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.repository.BookingRepository;
import com.athlefit.backend.repository.CourtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvailabilityServiceTest {

    private static final UUID VENUE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VENUE_SPORT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    // 2026-09-01 is a Tuesday; "now" is 09:30 Jakarta time on that day.
    private static final LocalDate DATE = LocalDate.of(2026, 9, 1);

    private BookingRepository bookingRepository;
    private AvailabilityService availabilityService;
    private Court courtOne;
    private Court courtTwo;

    @BeforeEach
    void setUp() {
        VenueService venueService = mock(VenueService.class);
        CourtRepository courtRepository = mock(CourtRepository.class);
        bookingRepository = mock(BookingRepository.class);
        Venue venue = mock(Venue.class);
        VenueSport venueSport = mock(VenueSport.class);
        Sport sport = mock(Sport.class);
        courtOne = court(UUID.fromString("40000000-0000-0000-0000-000000000001"), venueSport);
        courtTwo = court(UUID.fromString("40000000-0000-0000-0000-000000000002"), venueSport);

        when(venue.getId()).thenReturn(VENUE_ID);
        when(venue.getOpenDays()).thenReturn(Set.of(DayOfWeek.TUESDAY));
        when(venue.getOpenTime()).thenReturn(LocalTime.of(8, 0));
        when(venue.getCloseTime()).thenReturn(LocalTime.of(12, 0));
        when(sport.getSlug()).thenReturn("badminton");
        when(venueSport.getId()).thenReturn(VENUE_SPORT_ID);
        when(venueSport.getVenue()).thenReturn(venue);
        when(venueSport.getSport()).thenReturn(sport);
        when(venueSport.getHourlyRate()).thenReturn(new BigDecimal("60000.00"));
        when(venueService.requireVenueSport(VENUE_ID, "badminton")).thenReturn(venueSport);
        when(courtRepository.findAllByVenueSportIdAndActiveTrue(VENUE_SPORT_ID))
                .thenReturn(List.of(courtOne, courtTwo));

        availabilityService = new AvailabilityService(
                venueService,
                courtRepository,
                bookingRepository,
                Clock.fixed(Instant.parse("2026-09-01T02:30:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void marksPastFullAndAvailableSlots() {
        OffsetDateTime tenOClock = OffsetDateTime.parse("2026-09-01T10:00:00+07:00");
        Booking first = booking(courtOne, tenOClock, OffsetDateTime.parse("2026-09-01T09:00:00Z"));
        Booking second = booking(courtTwo, tenOClock, OffsetDateTime.parse("2026-09-01T09:00:00Z"));
        when(bookingRepository.findOverlapping(any(), any(), any(), any()))
                .thenReturn(List.of(first, second));

        AvailabilityResponse result = availabilityService.findAvailability(
                VENUE_ID, "badminton", DATE, 1
        );

        assertTrue(result.open());
        assertEquals(List.of("08:00", "09:00", "10:00", "11:00"),
                result.slots().stream().map(AvailabilityResponse.Slot::startTime).toList());
        assertEquals(List.of("PAST", "PAST", "FULL", "AVAILABLE"),
                result.slots().stream().map(AvailabilityResponse.Slot::status).toList());
        assertEquals(2, result.slots().get(3).availableCourts());
        assertEquals("+07:00", result.slots().get(3).startAt().getOffset().getId());
    }

    @Test
    void unpaidBookingsPastTheirDeadlineDoNotBlockSlots() {
        OffsetDateTime tenOClock = OffsetDateTime.parse("2026-09-01T10:00:00+07:00");
        Booking overdue = booking(courtOne, tenOClock, OffsetDateTime.parse("2026-09-01T02:00:00Z"));
        when(bookingRepository.findOverlapping(any(), any(), any(), any()))
                .thenReturn(List.of(overdue));

        AvailabilityResponse result = availabilityService.findAvailability(
                VENUE_ID, "badminton", DATE, 1
        );

        assertEquals(2, result.slots().get(2).availableCourts());
    }

    @Test
    void longerDurationsOnlyOfferStartsThatFitBeforeClosing() {
        when(bookingRepository.findOverlapping(any(), any(), any(), any())).thenReturn(List.of());

        AvailabilityResponse result = availabilityService.findAvailability(
                VENUE_ID, "badminton", DATE, 3
        );

        assertEquals(List.of("08:00", "09:00"),
                result.slots().stream().map(AvailabilityResponse.Slot::startTime).toList());
    }

    @Test
    void closedDayHasNoSlots() {
        AvailabilityResponse result = availabilityService.findAvailability(
                VENUE_ID, "badminton", DATE.plusDays(1), 1
        );

        assertFalse(result.open());
        assertTrue(result.slots().isEmpty());
    }

    @Test
    void rejectsPastDates() {
        assertThrows(IllegalArgumentException.class, () -> availabilityService.findAvailability(
                VENUE_ID, "badminton", DATE.minusDays(1), 1
        ));
    }

    private static Court court(UUID id, VenueSport venueSport) {
        Court court = mock(Court.class);
        when(court.getId()).thenReturn(id);
        when(court.getVenueSport()).thenReturn(venueSport);
        return court;
    }

    private static Booking booking(Court court, OffsetDateTime startAt, OffsetDateTime deadline) {
        return new Booking(
                mock(UserAccount.class),
                court,
                startAt.withOffsetSameInstant(ZoneOffset.UTC),
                startAt.plusHours(1).withOffsetSameInstant(ZoneOffset.UTC),
                1,
                new BigDecimal("60000.00"),
                new BigDecimal("60000.00"),
                deadline
        );
    }
}
