package com.athlefit.backend.dto.request;

import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/** Partial update: null fields are left unchanged. Schedule fields must be sent together. */
public record UpdateVenueSettingsRequest(
        @Size(max = 50) String phone,
        Set<DayOfWeek> openDays,
        LocalTime openTime,
        LocalTime closeTime,
        Boolean active,
        @Size(max = 100) String bankName,
        @Size(max = 50) String bankAccountNumber,
        @Size(max = 255) String bankAccountHolder
) {
}
