package com.athlefit.backend.controller;

import com.athlefit.backend.dto.response.AvailabilityResponse;
import com.athlefit.backend.dto.response.SportResponse;
import com.athlefit.backend.dto.response.VenueResponse;
import com.athlefit.backend.service.AvailabilityService;
import com.athlefit.backend.service.VenueService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class VenueController {

    private final VenueService venueService;
    private final AvailabilityService availabilityService;

    public VenueController(VenueService venueService, AvailabilityService availabilityService) {
        this.venueService = venueService;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/sports")
    public List<SportResponse> getSports() {
        return venueService.findSports();
    }

    @GetMapping("/venues")
    public List<VenueResponse> getVenues(@RequestParam(required = false) String category) {
        return venueService.findVenues(category);
    }

    @GetMapping("/venues/{id}")
    public VenueResponse getVenue(
            @PathVariable UUID id,
            @RequestParam(required = false) String sport
    ) {
        return venueService.findVenue(id, sport);
    }

    @GetMapping("/venues/{id}/availability")
    public AvailabilityResponse getAvailability(
            @PathVariable UUID id,
            @RequestParam String sport,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int durationHours
    ) {
        return availabilityService.findAvailability(id, sport, date, durationHours);
    }

}
