package com.athlefit.backend.dto.response;

import com.athlefit.backend.model.Sport;

import java.util.UUID;

public record SportResponse(UUID id, String slug, String name) {
    public static SportResponse from(Sport sport) {
        return new SportResponse(sport.getId(), sport.getSlug(), sport.getName());
    }
}
