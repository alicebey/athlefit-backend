package com.athlefit.backend.dto.response;

import com.athlefit.backend.model.UserAccount;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String firebaseUid,
        String email,
        String fullName,
        String phone,
        String favoriteSport
) {
    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getFavoriteSport()
        );
    }
}
