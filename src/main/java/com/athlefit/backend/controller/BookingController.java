package com.athlefit.backend.controller;

import com.athlefit.backend.dto.request.CreateBookingRequest;
import com.athlefit.backend.dto.request.SubmitPaymentRequest;
import com.athlefit.backend.dto.response.BookingResponse;
import com.athlefit.backend.security.AuthenticatedUser;
import com.athlefit.backend.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/me")
    public List<BookingResponse> getMyBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.findMyBookings(AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{id}")
    public BookingResponse getBooking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return bookingService.findMyBooking(AuthenticatedUser.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return bookingService.create(AuthenticatedUser.from(jwt), request);
    }

    @PostMapping("/{id}/payment")
    public BookingResponse submitPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitPaymentRequest request
    ) {
        return bookingService.submitPayment(AuthenticatedUser.from(jwt), id, request);
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return bookingService.cancel(AuthenticatedUser.from(jwt), id);
    }
}
