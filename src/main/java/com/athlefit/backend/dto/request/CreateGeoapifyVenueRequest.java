package com.athlefit.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record CreateGeoapifyVenueRequest(
        @NotBlank String geoapifyPlaceId,
        @Valid ScheduleOverride scheduleOverride,
        @NotEmpty List<@NotNull @Valid SportConfiguration> sports
) {
    public record ScheduleOverride(
            @NotEmpty Set<@NotNull DayOfWeek> openDays,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime
    ) {
    }

    public record SportConfiguration(
            @NotBlank String sportSlug,
            @NotNull @DecimalMin(value = "1.00") BigDecimal hourlyRate,
            @Min(1) @Max(50) int courtCount
    ) {
    }
}
