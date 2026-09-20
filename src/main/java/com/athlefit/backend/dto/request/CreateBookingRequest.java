package com.athlefit.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID venueId,
        @NotBlank String sportSlug,
        @NotNull OffsetDateTime startAt,
        @Min(1) @Max(3) int durationHours
) {
}
