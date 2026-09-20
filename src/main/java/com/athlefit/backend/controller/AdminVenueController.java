package com.athlefit.backend.controller;

import com.athlefit.backend.dto.request.AssignOwnerRequest;
import com.athlefit.backend.dto.request.CreateGeoapifyVenueRequest;
import com.athlefit.backend.dto.response.AdminStatusResponse;
import com.athlefit.backend.dto.response.GeoapifyPlaceSearchResponse;
import com.athlefit.backend.dto.response.OwnerVenueResponse;
import com.athlefit.backend.dto.response.VenueResponse;
import com.athlefit.backend.security.AuthenticatedUser;
import com.athlefit.backend.service.AdminVenueService;
import com.athlefit.backend.service.OwnerVenueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminVenueController {

    private final AdminVenueService adminVenueService;
    private final OwnerVenueService ownerVenueService;

    public AdminVenueController(
            AdminVenueService adminVenueService,
            OwnerVenueService ownerVenueService
    ) {
        this.adminVenueService = adminVenueService;
        this.ownerVenueService = ownerVenueService;
    }

    @GetMapping("/status")
    public AdminStatusResponse getAdminStatus(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser identity = AuthenticatedUser.from(jwt);
        return new AdminStatusResponse(
                adminVenueService.isAdmin(identity),
                ownerVenueService.isOwner(identity)
        );
    }

    @GetMapping("/geoapify-places/search")
    public List<GeoapifyPlaceSearchResponse> searchGeoapifyPlaces(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String query
    ) {
        return adminVenueService.search(AuthenticatedUser.from(jwt), query);
    }

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public List<VenueResponse> createVenue(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateGeoapifyVenueRequest request
    ) {
        return adminVenueService.create(AuthenticatedUser.from(jwt), request);
    }

    @PutMapping("/venues/{id}/owner")
    public OwnerVenueResponse assignOwner(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AssignOwnerRequest request
    ) {
        return ownerVenueService.assignOwner(AuthenticatedUser.from(jwt), id, request);
    }
}
