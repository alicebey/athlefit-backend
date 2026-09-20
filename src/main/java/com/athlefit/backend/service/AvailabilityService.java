package com.athlefit.backend.service;

import com.athlefit.backend.dto.response.AvailabilityResponse;
import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.BookingStatus;
import com.athlefit.backend.model.Court;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.model.VenueTime;
import com.athlefit.backend.repository.BookingRepository;
import com.athlefit.backend.repository.CourtRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Computes which start times can still be booked, so customers pick a free slot instead of
 * guessing. Slots start on the venue's opening time and repeat every hour.
 */
@Service
public class AvailabilityService {

    public static final int MAX_DAYS_AHEAD = 60;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final VenueService venueService;
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public AvailabilityService(
            VenueService venueService,
            CourtRepository courtRepository,
            BookingRepository bookingRepository,
            Clock clock
    ) {
        this.venueService = venueService;
        this.courtRepository = courtRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse findAvailability(
            UUID venueId,
            String sportSlug,
            LocalDate date,
            int durationHours
    ) {
        if (durationHours < 1 || durationHours > 3) {
            throw new IllegalArgumentException("durationHours must be between 1 and 3");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        LocalDate today = now.atZoneSameInstant(VenueTime.ZONE).toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new IllegalArgumentException(
                    "Date must be between today and " + MAX_DAYS_AHEAD + " days ahead"
            );
        }

        VenueSport venueSport = venueService.requireVenueSport(venueId, sportSlug);
        Venue venue = venueSport.getVenue();
        List<Court> courts = courtRepository.findAllByVenueSportIdAndActiveTrue(venueSport.getId());
        boolean open = venue.getOpenDays().contains(date.getDayOfWeek())
                && venue.getOpenTime() != null
                && venue.getCloseTime() != null;

        List<AvailabilityResponse.Slot> slots = new ArrayList<>();
        if (open && !courts.isEmpty()) {
            OffsetDateTime dayStart = date.atTime(venue.getOpenTime())
                    .atZone(VenueTime.ZONE).toOffsetDateTime();
            OffsetDateTime dayEnd = date.atTime(venue.getCloseTime())
                    .atZone(VenueTime.ZONE).toOffsetDateTime();
            List<Booking> holds = bookingRepository.findOverlapping(
                    venueSport.getId(),
                    BookingStatus.ACTIVE,
                    dayStart,
                    dayEnd
            ).stream().filter(booking -> !booking.isPaymentOverdue(now)).toList();

            LocalTime start = venue.getOpenTime();
            while (!start.plusHours(durationHours).isAfter(venue.getCloseTime())
                    && start.plusHours(durationHours).isAfter(start)) {
                LocalDateTime localStart = date.atTime(start);
                OffsetDateTime slotStart = localStart.atZone(VenueTime.ZONE).toOffsetDateTime();
                OffsetDateTime slotEnd = slotStart.plusHours(durationHours);
                int freeCourts = countFreeCourts(courts, holds, slotStart, slotEnd);
                String status = !slotStart.isAfter(now)
                        ? "PAST"
                        : freeCourts == 0 ? "FULL" : "AVAILABLE";
                slots.add(new AvailabilityResponse.Slot(
                        slotStart,
                        slotEnd,
                        start.format(TIME_FORMAT),
                        start.plusHours(durationHours).format(TIME_FORMAT),
                        "PAST".equals(status) ? 0 : freeCourts,
                        status
                ));
                start = start.plusHours(1);
            }
        }

        return new AvailabilityResponse(
                venue.getId(),
                venueSport.getSport().getSlug(),
                date,
                VenueTime.ZONE.getId(),
                durationHours,
                open,
                format(venue.getOpenTime()),
                format(venue.getCloseTime()),
                venueSport.getHourlyRate(),
                courts.size(),
                slots
        );
    }

    private int countFreeCourts(
            List<Court> courts,
            List<Booking> holds,
            OffsetDateTime slotStart,
            OffsetDateTime slotEnd
    ) {
        int free = 0;
        for (Court court : courts) {
            boolean taken = holds.stream().anyMatch(booking ->
                    booking.getCourt().getId().equals(court.getId())
                            && booking.getStartAt().isBefore(slotEnd)
                            && booking.getEndAt().isAfter(slotStart));
            if (!taken) {
                free++;
            }
        }
        return free;
    }

    private String format(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
