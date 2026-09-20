package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.AssignOwnerRequest;
import com.athlefit.backend.dto.request.RejectPaymentRequest;
import com.athlefit.backend.dto.request.UpdateVenueSettingsRequest;
import com.athlefit.backend.dto.request.UpsertVenueSportRequest;
import com.athlefit.backend.dto.response.BookingResponse;
import com.athlefit.backend.dto.response.OwnerVenueResponse;
import com.athlefit.backend.exception.ConflictException;
import com.athlefit.backend.exception.ForbiddenException;
import com.athlefit.backend.exception.NotFoundException;
import com.athlefit.backend.model.Booking;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Venue management for venue owners and platform admins: settings, prices, court inventory, and
 * manual payment confirmation. Admins can manage every venue; owners only the venues assigned to
 * them.
 */
@Service
public class OwnerVenueService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final VenueRepository venueRepository;
    private final VenueSportRepository venueSportRepository;
    private final SportRepository sportRepository;
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final UserAccountRepository userAccountRepository;
    private final AdminVenueService adminVenueService;
    private final BookingService bookingService;
    private final Clock clock;

    public OwnerVenueService(
            VenueRepository venueRepository,
            VenueSportRepository venueSportRepository,
            SportRepository sportRepository,
            CourtRepository courtRepository,
            BookingRepository bookingRepository,
            UserAccountRepository userAccountRepository,
            AdminVenueService adminVenueService,
            BookingService bookingService,
            Clock clock
    ) {
        this.venueRepository = venueRepository;
        this.venueSportRepository = venueSportRepository;
        this.sportRepository = sportRepository;
        this.courtRepository = courtRepository;
        this.bookingRepository = bookingRepository;
        this.userAccountRepository = userAccountRepository;
        this.adminVenueService = adminVenueService;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(AuthenticatedUser identity) {
        return venueRepository.existsByOwnerFirebaseUid(identity.uid());
    }

    @Transactional(readOnly = true)
    public List<OwnerVenueResponse> findManagedVenues(AuthenticatedUser identity) {
        List<Venue> venues = adminVenueService.isAdmin(identity)
                ? venueRepository.findAllByOrderByNameAsc()
                : venueRepository.findAllByOwnerFirebaseUidOrderByNameAsc(identity.uid());
        OffsetDateTime now = OffsetDateTime.now(clock);
        return venues.stream().map(venue -> toResponse(venue, now)).toList();
    }

    @Transactional(readOnly = true)
    public OwnerVenueResponse findManagedVenue(AuthenticatedUser identity, UUID venueId) {
        return toResponse(requireManagedVenue(identity, venueId), OffsetDateTime.now(clock));
    }

    @Transactional
    public OwnerVenueResponse updateSettings(
            AuthenticatedUser identity,
            UUID venueId,
            UpdateVenueSettingsRequest request
    ) {
        Venue venue = requireManagedVenue(identity, venueId);
        if (request.phone() != null) {
            venue.updatePhone(normalizeOptional(request.phone()));
        }
        boolean scheduleChanged = request.openDays() != null
                || request.openTime() != null
                || request.closeTime() != null;
        if (scheduleChanged) {
            if (request.openDays() == null || request.openTime() == null
                    || request.closeTime() == null) {
                throw new IllegalArgumentException(
                        "openDays, openTime and closeTime must be updated together"
                );
            }
            if (request.openDays().isEmpty()) {
                throw new IllegalArgumentException("At least one opening day is required");
            }
            if (!request.closeTime().isAfter(request.openTime())) {
                throw new IllegalArgumentException("closeTime must be after openTime");
            }
            venue.updateSchedule(
                    Set.copyOf(request.openDays()),
                    request.openTime(),
                    request.closeTime()
            );
        }
        boolean paymentChanged = request.bankName() != null
                || request.bankAccountNumber() != null
                || request.bankAccountHolder() != null;
        if (paymentChanged) {
            String bankName = normalizeOptional(request.bankName());
            String accountNumber = normalizeOptional(request.bankAccountNumber());
            String accountHolder = normalizeOptional(request.bankAccountHolder());
            boolean allBlank = bankName == null && accountNumber == null && accountHolder == null;
            boolean allPresent = bankName != null && accountNumber != null && accountHolder != null;
            if (!allBlank && !allPresent) {
                throw new IllegalArgumentException(
                        "Bank name, account number and account holder are all required"
                );
            }
            venue.updatePaymentAccount(bankName, accountNumber, accountHolder);
        }
        if (request.active() != null) {
            venue.updateActive(request.active());
        }
        return toResponse(venue, OffsetDateTime.now(clock));
    }

    /** Creates or updates a sport offering and grows/shrinks its active court inventory. */
    @Transactional
    public OwnerVenueResponse upsertSport(
            AuthenticatedUser identity,
            UUID venueId,
            String sportSlug,
            UpsertVenueSportRequest request
    ) {
        Venue venue = requireManagedVenue(identity, venueId);
        String slug = sportSlug.trim().toLowerCase(Locale.ROOT);
        VenueSport venueSport = venueSportRepository.findByVenueIdAndSportSlug(venueId, slug)
                .orElseGet(() -> {
                    Sport sport = sportRepository.findBySlug(slug)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Unknown sport: " + sportSlug
                            ));
                    return venueSportRepository.save(
                            new VenueSport(venue, sport, request.hourlyRate())
                    );
                });
        venueSport.updateHourlyRate(request.hourlyRate());
        resizeCourts(venueSport, request.courtCount(), OffsetDateTime.now(clock));
        return toResponse(venue, OffsetDateTime.now(clock));
    }

    @Transactional
    public List<BookingResponse> findVenueBookings(AuthenticatedUser identity, UUID venueId) {
        requireManagedVenue(identity, venueId);
        bookingService.expireOverduePayments();
        OffsetDateTime now = OffsetDateTime.now(clock);
        return bookingRepository.findVenueBookingsEndingAfter(venueId, now.minusDays(7)).stream()
                .map(booking -> bookingService.toResponse(booking, now))
                .toList();
    }

    @Transactional
    public BookingResponse confirmPayment(AuthenticatedUser identity, UUID bookingId) {
        Booking booking = requireManagedBooking(identity, bookingId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        booking.confirmPayment(now);
        return bookingService.toResponse(booking, now);
    }

    @Transactional
    public BookingResponse rejectPayment(
            AuthenticatedUser identity,
            UUID bookingId,
            RejectPaymentRequest request
    ) {
        Booking booking = requireManagedBooking(identity, bookingId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        booking.rejectPayment(request.reason().trim(), now);
        return bookingService.toResponse(booking, now);
    }

    /** Admin only: links a venue to a registered user, or unlinks it when email is blank. */
    @Transactional
    public OwnerVenueResponse assignOwner(
            AuthenticatedUser identity,
            UUID venueId,
            AssignOwnerRequest request
    ) {
        if (!adminVenueService.isAdmin(identity)) {
            throw new ForbiddenException("Only an Athlefit admin can assign venue owners");
        }
        Venue venue = venueRepository.findWithOwnerById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue was not found"));
        String email = normalizeOptional(request.email());
        if (email == null) {
            venue.assignOwner(null);
        } else {
            List<UserAccount> users = userAccountRepository.findAllByEmailIgnoreCase(email);
            if (users.isEmpty()) {
                throw new NotFoundException(
                        "No Athlefit account uses this email. Ask the owner to sign up first."
                );
            }
            if (users.size() > 1) {
                throw new ConflictException("More than one account uses this email");
            }
            venue.assignOwner(users.get(0));
        }
        return toResponse(venue, OffsetDateTime.now(clock));
    }

    private void resizeCourts(VenueSport venueSport, int targetCount, OffsetDateTime now) {
        List<Court> courts = new ArrayList<>(courtRepository.findAllByVenueSportId(venueSport.getId()));
        courts.sort(Comparator.comparingInt(OwnerVenueService::courtNumber)
                .thenComparing(Court::getName));
        List<Court> active = courts.stream().filter(Court::isActive).toList();

        if (active.size() > targetCount) {
            List<Court> toDeactivate = active.subList(targetCount, active.size());
            for (Court court : toDeactivate) {
                if (bookingRepository.existsByCourtIdAndStatusInAndEndAtAfter(
                        court.getId(), BookingStatus.ACTIVE, now)) {
                    throw new ConflictException(
                            court.getName() + " has upcoming bookings and cannot be removed yet"
                    );
                }
            }
            toDeactivate.forEach(Court::deactivate);
            return;
        }

        int missing = targetCount - active.size();
        for (Court court : courts) {
            if (missing == 0) {
                return;
            }
            if (!court.isActive()) {
                court.activate();
                missing--;
            }
        }
        int nextNumber = courts.stream().mapToInt(OwnerVenueService::courtNumber).max().orElse(0) + 1;
        List<Court> created = new ArrayList<>();
        for (int index = 0; index < missing; index++) {
            created.add(new Court(venueSport, "Court " + (nextNumber + index)));
        }
        courtRepository.saveAll(created);
    }

    private static int courtNumber(Court court) {
        String digits = court.getName().replaceAll("\\D+", "");
        if (digits.isEmpty() || digits.length() > 6) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private Venue requireManagedVenue(AuthenticatedUser identity, UUID venueId) {
        Venue venue = venueRepository.findWithOwnerById(venueId)
                .orElseThrow(() -> new NotFoundException("Venue was not found"));
        if (!adminVenueService.isAdmin(identity) && !venue.isOwnedBy(identity.uid())) {
            throw new ForbiddenException("You do not manage this venue");
        }
        return venue;
    }

    private Booking requireManagedBooking(AuthenticatedUser identity, UUID bookingId) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking was not found"));
        Venue venue = booking.getVenue();
        if (!adminVenueService.isAdmin(identity) && !venue.isOwnedBy(identity.uid())) {
            throw new ForbiddenException("You do not manage this venue");
        }
        return booking;
    }

    private OwnerVenueResponse toResponse(Venue venue, OffsetDateTime now) {
        List<VenueSport> venueSports = venueSportRepository.findAllByVenueIdOrderBySportName(venue.getId());
        long awaiting = bookingRepository.findVenueBookingsEndingAfter(venue.getId(), now).stream()
                .filter(booking -> booking.getStatus() == BookingStatus.WAITING_CONFIRMATION)
                .count();
        UserAccount owner = venue.getOwner();
        return new OwnerVenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getPhone(),
                venue.isActive(),
                venue.getSource().name(),
                venue.getOpenDays().stream()
                        .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                        .map(day -> day.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                        .toList(),
                format(venue.getOpenTime()),
                format(venue.getCloseTime()),
                venue.getBankName(),
                venue.getBankAccountNumber(),
                venue.getBankAccountHolder(),
                owner == null ? null : owner.getEmail(),
                owner == null ? null : owner.getFullName(),
                venueSports.stream()
                        .map(venueSport -> new OwnerVenueResponse.SportSettings(
                                venueSport.getSport().getSlug(),
                                venueSport.getSport().getName(),
                                venueSport.getHourlyRate(),
                                venueSport.getId() == null
                                        ? 0
                                        : courtRepository.countByVenueSportIdAndActiveTrue(
                                                venueSport.getId()
                                        )
                        ))
                        .toList(),
                awaiting
        );
    }

    private String format(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
