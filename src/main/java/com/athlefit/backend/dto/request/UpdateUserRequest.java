package com.athlefit.backend.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 255) String fullName,
        @Size(max = 50) String phone,
        @Size(max = 100) String favoriteSport
) {
}
