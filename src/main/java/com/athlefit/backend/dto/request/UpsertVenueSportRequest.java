package com.athlefit.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertVenueSportRequest(
        @NotNull @DecimalMin(value = "1.00") BigDecimal hourlyRate,
        @Min(1) @Max(50) int courtCount
) {
}
