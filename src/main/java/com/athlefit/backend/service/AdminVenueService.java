package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.CreateGeoapifyVenueRequest;
import com.athlefit.backend.dto.response.GeoapifyPlaceSearchResponse;
import com.athlefit.backend.dto.response.VenueResponse;
import com.athlefit.backend.exception.ForbiddenException;
import com.athlefit.backend.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminVenueService {

    private static final Map<String, String> TAGGED_SPORT_TO_SLUG = Map.ofEntries(
            Map.entry("badminton", "badminton"),
            Map.entry("futsal", "futsal"),
            Map.entry("basketball", "basketball"),
            Map.entry("soccer", "soccer"),
            Map.entry("football", "soccer"),
            Map.entry("tennis", "tennis"),
            Map.entry("golf", "golf"),
            Map.entry("billiards", "billiard"),
            Map.entry("snooker", "billiard")
    );
    private static final Map<String, String> CATEGORY_TO_SPORT = Map.of(
            "sport.golf_course", "golf"
    );
    private static final Map<String, String> NAME_KEYWORD_TO_SPORT = Map.ofEntries(
            Map.entry("badminton", "badminton"),
            Map.entry("futsal", "futsal"),
            Map.entry("basket", "basketball"),
            Map.entry("football", "soccer"),
            Map.entry("sepak bola", "soccer"),
            Map.entry("soccer", "soccer"),
            Map.entry("tennis", "tennis"),
            Map.entry("golf", "golf"),
            Map.entry("billiard", "billiard"),
            Map.entry("snooker", "billiard")
    );

    private final GeoapifyClient geoapifyClient;
    private final VenueService venueService;
    private final Set<String> adminFirebaseUids;

    public AdminVenueService(
            GeoapifyClient geoapifyClient,
            VenueService venueService,
            @Value("${athlefit.admin.firebase-uids:}") String adminFirebaseUids
    ) {
        this.geoapifyClient = geoapifyClient;
        this.venueService = venueService;
        this.adminFirebaseUids = Arrays.stream(adminFirebaseUids.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<GeoapifyPlaceSearchResponse> search(
            AuthenticatedUser identity,
            String query
    ) {
        requireAdmin(identity);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query cannot be blank");
        }
        return geoapifyClient.search(query.trim()).stream()
                .map(place -> GeoapifyPlaceSearchResponse.from(place, suggestSports(place)))
                .toList();
    }

    public List<VenueResponse> create(
            AuthenticatedUser identity,
            CreateGeoapifyVenueRequest request
    ) {
        requireAdmin(identity);
        ensureUniqueSports(request.sports());
        GeoapifyPlace place = geoapifyClient.getPlace(request.geoapifyPlaceId().trim());
        validatePlace(place);
        GeoapifyPlace.OpeningSchedule schedule = request.scheduleOverride() == null
                ? place.suggestedSchedule().orElseThrow(() -> new IllegalArgumentException(
                        "Geoapify hours are missing or too complex; provide scheduleOverride"
                ))
                : scheduleFrom(request.scheduleOverride());
        validateSchedule(schedule);
        return venueService.createGeoapifyVenue(place, schedule, request.sports());
    }

    List<String> suggestSports(GeoapifyPlace place) {
        Set<String> suggestions = new LinkedHashSet<>();
        place.taggedSports().stream()
                .map(value -> TAGGED_SPORT_TO_SLUG.get(value.toLowerCase(Locale.ROOT)))
                .filter(value -> value != null)
                .forEach(suggestions::add);
        place.categories().stream()
                .map(CATEGORY_TO_SPORT::get)
                .filter(value -> value != null)
                .forEach(suggestions::add);
        String searchableName = place.name() == null
                ? ""
                : place.name().toLowerCase(Locale.ROOT);
        NAME_KEYWORD_TO_SPORT.forEach((keyword, sport) -> {
            if (searchableName.contains(keyword)) {
                suggestions.add(sport);
            }
        });
        return new ArrayList<>(suggestions);
    }

    public boolean isAdmin(AuthenticatedUser identity) {
        return adminFirebaseUids.contains(identity.uid());
    }

    private GeoapifyPlace.OpeningSchedule scheduleFrom(
            CreateGeoapifyVenueRequest.ScheduleOverride override
    ) {
        return new GeoapifyPlace.OpeningSchedule(
                override.openDays().stream().sorted().toList(),
                override.openTime(),
                override.closeTime()
        );
    }

    private void requireAdmin(AuthenticatedUser identity) {
        if (!isAdmin(identity)) {
            throw new ForbiddenException("This Firebase user is not an Athlefit admin");
        }
    }

    private void ensureUniqueSports(
            List<CreateGeoapifyVenueRequest.SportConfiguration> sports
    ) {
        long uniqueSports = sports.stream()
                .map(configuration -> configuration.sportSlug().trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (uniqueSports != sports.size()) {
            throw new IllegalArgumentException("Each sport can only be configured once");
        }
    }

    private void validatePlace(GeoapifyPlace place) {
        if (place.placeId() == null || place.name() == null || place.address() == null
                || place.latitude() == null || place.longitude() == null) {
            throw new IllegalArgumentException("Geoapify place is missing required location data");
        }
    }

    private void validateSchedule(GeoapifyPlace.OpeningSchedule schedule) {
        if (schedule.openDays().isEmpty()) {
            throw new IllegalArgumentException("At least one opening day is required");
        }
        if (!schedule.closeTime().isAfter(schedule.openTime())) {
            throw new IllegalArgumentException("closeTime must be after openTime");
        }
    }
}
