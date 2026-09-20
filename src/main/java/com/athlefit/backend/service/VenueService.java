package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.CreateGeoapifyVenueRequest;
import com.athlefit.backend.dto.response.SportResponse;
import com.athlefit.backend.dto.response.VenueResponse;
import com.athlefit.backend.exception.ConflictException;
import com.athlefit.backend.exception.NotFoundException;
import com.athlefit.backend.model.Court;
import com.athlefit.backend.model.Sport;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueSource;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.repository.CourtRepository;
import com.athlefit.backend.repository.SportRepository;
import com.athlefit.backend.repository.VenueRepository;
import com.athlefit.backend.repository.VenueSportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class VenueService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String GEOAPIFY_ATTRIBUTION = "Powered by Geoapify";
    private static final String GEOAPIFY_URI = "https://www.geoapify.com/";
    private static final String OSM_ATTRIBUTION = "© OpenStreetMap contributors";
    private static final String OSM_URI = "https://www.openstreetmap.org/copyright";

    private final VenueRepository venueRepository;
    private final VenueSportRepository venueSportRepository;
    private final SportRepository sportRepository;
    private final CourtRepository courtRepository;

    public VenueService(
            VenueRepository venueRepository,
            VenueSportRepository venueSportRepository,
            SportRepository sportRepository,
            CourtRepository courtRepository
    ) {
        this.venueRepository = venueRepository;
        this.venueSportRepository = venueSportRepository;
        this.sportRepository = sportRepository;
        this.courtRepository = courtRepository;
    }

    /**
     * Lists active venues once each. When a category is given, only venues offering that sport are
     * returned and the top-level price/court fields describe that sport.
     */
    @Transactional(readOnly = true)
    public List<VenueResponse> findVenues(String category) {
        String sportSlug = category == null || category.isBlank()
                ? null
                : category.trim().toLowerCase(Locale.ROOT);
        Map<UUID, List<VenueSport>> sportsByVenue = new LinkedHashMap<>();
        for (VenueSport venueSport : venueSportRepository.findAllByVenueActiveTrue()) {
            sportsByVenue
                    .computeIfAbsent(venueSport.getVenue().getId(), id -> new ArrayList<>())
                    .add(venueSport);
        }
        List<VenueResponse> responses = new ArrayList<>();
        for (List<VenueSport> venueSports : sportsByVenue.values()) {
            venueSports.sort(Comparator.comparing(
                    venueSport -> venueSport.getSport().getName(),
                    String.CASE_INSENSITIVE_ORDER
            ));
            Optional<VenueSport> primary = sportSlug == null
                    ? Optional.of(venueSports.get(0))
                    : venueSports.stream()
                            .filter(venueSport -> venueSport.getSport().getSlug().equals(sportSlug))
                            .findFirst();
            primary.ifPresent(venueSport -> responses.add(toResponse(venueSport, venueSports)));
        }
        responses.sort(Comparator.comparing(VenueResponse::name, String.CASE_INSENSITIVE_ORDER));
        return responses;
    }

    @Transactional(readOnly = true)
    public VenueResponse findVenue(UUID id, String sport) {
        List<VenueSport> venueSports = venueSportRepository.findAllByVenueIdOrderBySportName(id);
        if (venueSports.isEmpty() || !venueSports.get(0).getVenue().isActive()) {
            throw new NotFoundException("Venue was not found");
        }
        VenueSport primary = venueSports.get(0);
        if (sport != null && !sport.isBlank()) {
            String sportSlug = sport.trim().toLowerCase(Locale.ROOT);
            primary = venueSports.stream()
                    .filter(venueSport -> venueSport.getSport().getSlug().equals(sportSlug))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Venue does not offer the selected sport"
                    ));
        }
        return toResponse(primary, venueSports);
    }

    @Transactional(readOnly = true)
    public List<SportResponse> findSports() {
        return sportRepository.findAllByOrderByNameAsc().stream()
                .map(SportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VenueSport requireVenueSport(UUID venueId, String sportSlug) {
        VenueSport venueSport = venueSportRepository.findByVenueIdAndSportSlug(
                        venueId,
                        sportSlug.trim().toLowerCase(Locale.ROOT)
                )
                .orElseThrow(() -> new NotFoundException(
                        "Venue does not offer the selected sport"
                ));
        if (!venueSport.getVenue().isActive()) {
            throw new NotFoundException("Venue is not accepting bookings right now");
        }
        return venueSport;
    }

    public void validateOperatingHours(
            Venue venue,
            ZonedDateTime localStart,
            ZonedDateTime localEnd
    ) {
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            throw new IllegalArgumentException("Booking must start and end on the same day");
        }
        if (!venue.getOpenDays().contains(localStart.getDayOfWeek())) {
            throw new IllegalArgumentException("Venue is closed on the selected day");
        }
        if (localStart.toLocalTime().isBefore(venue.getOpenTime())
                || localEnd.toLocalTime().isAfter(venue.getCloseTime())) {
            throw new IllegalArgumentException("Booking time is outside venue operating hours");
        }
    }

    @Transactional
    public List<VenueResponse> createGeoapifyVenue(
            GeoapifyPlace place,
            GeoapifyPlace.OpeningSchedule schedule,
            List<CreateGeoapifyVenueRequest.SportConfiguration> configurations
    ) {
        if (venueRepository.existsByProviderPlaceId(place.placeId())) {
            throw new ConflictException("This Geoapify place is already an Athlefit venue");
        }
        Venue venue = venueRepository.save(Venue.geoapify(
                place.placeId(),
                place.name(),
                place.address(),
                place.latitude(),
                place.longitude(),
                place.phone(),
                Set.copyOf(schedule.openDays()),
                schedule.openTime(),
                schedule.closeTime()
        ));
        List<VenueResponse> responses = new ArrayList<>();
        for (CreateGeoapifyVenueRequest.SportConfiguration configuration : configurations) {
            Sport sport = sportRepository.findBySlug(
                            configuration.sportSlug().trim().toLowerCase(Locale.ROOT)
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown sport: " + configuration.sportSlug()
                    ));
            VenueSport venueSport = venueSportRepository.save(new VenueSport(
                    venue,
                    sport,
                    configuration.hourlyRate()
            ));
            List<Court> courts = new ArrayList<>();
            for (int index = 1; index <= configuration.courtCount(); index++) {
                courts.add(new Court(venueSport, "Court " + index));
            }
            courtRepository.saveAll(courts);
            responses.add(toResponse(venueSport));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public VenueResponse toResponse(VenueSport venueSport) {
        UUID venueId = venueSport.getVenue().getId();
        List<VenueSport> siblings = venueId == null
                ? List.of()
                : venueSportRepository.findAllByVenueIdOrderBySportName(venueId);
        return toResponse(venueSport, siblings);
    }

    private VenueResponse toResponse(VenueSport venueSport, List<VenueSport> venueSports) {
        Venue venue = venueSport.getVenue();
        List<VenueSport> offered = venueSports == null || venueSports.isEmpty()
                ? List.of(venueSport)
                : venueSports;
        boolean geoapify = VenueSource.GEOAPIFY.equals(venue.getSource());
        return new VenueResponse(
                venue.getId(),
                venueSport.getSport().getSlug(),
                venue.getName(),
                venue.getAddress(),
                new VenueResponse.Coordinates(venue.getLatitude(), venue.getLongitude()),
                venue.getRating(),
                venue.getImageUrl(),
                venue.getPhone(),
                formatDays(venue.getOpenDays().stream()
                        .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                        .toList()),
                formatTime(venue.getOpenTime()),
                formatTime(venue.getCloseTime()),
                venueSport.getHourlyRate(),
                courtRepository.countByVenueSportIdAndActiveTrue(venueSport.getId()),
                venue.getSource().name(),
                geoapify ? GEOAPIFY_ATTRIBUTION : null,
                geoapify ? GEOAPIFY_URI : null,
                geoapify ? OSM_ATTRIBUTION : null,
                geoapify ? OSM_URI : null,
                offered.stream()
                        .map(offering -> new VenueResponse.SportOffering(
                                offering.getSport().getSlug(),
                                offering.getSport().getName(),
                                offering.getHourlyRate(),
                                offering.getId() == null
                                        ? 0
                                        : courtRepository.countByVenueSportIdAndActiveTrue(
                                                offering.getId()
                                        )
                        ))
                        .toList()
        );
    }

    private List<String> formatDays(List<DayOfWeek> days) {
        return days.stream()
                .map(day -> day.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .toList();
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.format(TIME_FORMAT);
    }
}
