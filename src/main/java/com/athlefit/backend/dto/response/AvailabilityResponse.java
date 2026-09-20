package com.athlefit.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Bookable start times for one venue sport on one venue-local date. */
public record AvailabilityResponse(
        UUID venueId,
        String sportSlug,
        LocalDate date,
        String timeZone,
        int durationHours,
        boolean open,
        String openTime,
        String closeTime,
        BigDecimal hourlyRate,
        long totalCourts,
        List<Slot> slots
) {
    /**
     * @param status AVAILABLE, FULL or PAST
     */
    public record Slot(
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String startTime,
            String endTime,
            int availableCourts,
            String status
    ) {
    }
}
