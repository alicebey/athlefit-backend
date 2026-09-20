package com.athlefit.backend.controller;

import com.athlefit.backend.dto.request.RejectPaymentRequest;
import com.athlefit.backend.dto.request.UpdateVenueSettingsRequest;
import com.athlefit.backend.dto.request.UpsertVenueSportRequest;
import com.athlefit.backend.dto.response.BookingResponse;
import com.athlefit.backend.dto.response.OwnerVenueResponse;
import com.athlefit.backend.security.AuthenticatedUser;
import com.athlefit.backend.service.OwnerVenueService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Venue management for venue owners (and admins, who can manage every venue). */
@RestController
@RequestMapping("/api/v1/owner")
public class OwnerController {

    private final OwnerVenueService ownerVenueService;

    public OwnerController(OwnerVenueService ownerVenueService) {
        this.ownerVenueService = ownerVenueService;
    }

    @GetMapping("/venues")
    public List<OwnerVenueResponse> getVenues(@AuthenticationPrincipal Jwt jwt) {
        return ownerVenueService.findManagedVenues(AuthenticatedUser.from(jwt));
    }

    @GetMapping("/venues/{id}")
    public OwnerVenueResponse getVenue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ownerVenueService.findManagedVenue(AuthenticatedUser.from(jwt), id);
    }

    @PatchMapping("/venues/{id}")
    public OwnerVenueResponse updateVenue(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVenueSettingsRequest request
    ) {
        return ownerVenueService.updateSettings(AuthenticatedUser.from(jwt), id, request);
    }

    @PutMapping("/venues/{id}/sports/{sportSlug}")
    public OwnerVenueResponse upsertSport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @PathVariable String sportSlug,
            @Valid @RequestBody UpsertVenueSportRequest request
    ) {
        return ownerVenueService.upsertSport(AuthenticatedUser.from(jwt), id, sportSlug, request);
    }

    @GetMapping("/venues/{id}/bookings")
    public List<BookingResponse> getVenueBookings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return ownerVenueService.findVenueBookings(AuthenticatedUser.from(jwt), id);
    }

    @PatchMapping("/bookings/{id}/confirm-payment")
    public BookingResponse confirmPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return ownerVenueService.confirmPayment(AuthenticatedUser.from(jwt), id);
    }

    @PatchMapping("/bookings/{id}/reject-payment")
    public BookingResponse rejectPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody RejectPaymentRequest request
    ) {
        return ownerVenueService.rejectPayment(AuthenticatedUser.from(jwt), id, request);
    }
}
