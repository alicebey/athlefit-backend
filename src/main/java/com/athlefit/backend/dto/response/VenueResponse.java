package com.athlefit.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String category,
        String name,
        String address,
        Coordinates coordinates,
        BigDecimal rating,
        String imageUrl,
        String phone,
        List<String> openDays,
        String openTime,
        String closeTime,
        BigDecimal hourlyRate,
        long courtCount,
        String source,
        String providerAttribution,
        String providerAttributionUri,
        String dataAttribution,
        String dataAttributionUri,
        List<SportOffering> sports
) {
    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }

    /** One sport the venue offers, with its own price and court inventory. */
    public record SportOffering(String slug, String name, BigDecimal hourlyRate, long courtCount) {
    }
}
