package com.athlefit.backend.security;

import org.springframework.security.oauth2.jwt.Jwt;

public record AuthenticatedUser(String uid, String email, String name) {

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")
        );
    }
}
