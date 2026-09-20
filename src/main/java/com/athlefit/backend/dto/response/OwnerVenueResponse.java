package com.athlefit.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Venue configuration as managed by its owner or an admin. */
public record OwnerVenueResponse(
        UUID id,
        String name,
        String address,
        String phone,
        boolean active,
        String source,
        List<String> openDays,
        String openTime,
        String closeTime,
        String bankName,
        String bankAccountNumber,
        String bankAccountHolder,
        String ownerEmail,
        String ownerName,
        List<SportSettings> sports,
        long bookingsAwaitingConfirmation
) {
    public record SportSettings(String slug, String name, BigDecimal hourlyRate, long courtCount) {
    }
}
