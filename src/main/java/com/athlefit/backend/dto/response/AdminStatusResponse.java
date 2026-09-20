package com.athlefit.backend.dto.response;

/** Roles of the current user: platform admin and/or owner of at least one venue. */
public record AdminStatusResponse(boolean admin, boolean owner) {
}
