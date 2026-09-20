package com.athlefit.backend.dto.response;

import com.athlefit.backend.service.GeoapifyPlace;

import java.util.List;
import java.util.Set;

public record GeoapifyPlaceSearchResponse(
        String geoapifyPlaceId,
        String name,
        String address,
        VenueResponse.Coordinates coordinates,
        Set<String> categories,
        Set<String> taggedSports,
        String openingHours,
        GeoapifyPlace.OpeningSchedule suggestedSchedule,
        List<String> suggestedSports
) {
    public static GeoapifyPlaceSearchResponse from(
            GeoapifyPlace place,
            List<String> suggestedSports
    ) {
        return new GeoapifyPlaceSearchResponse(
                place.placeId(),
                place.name(),
                place.address(),
                new VenueResponse.Coordinates(place.latitude(), place.longitude()),
                place.categories(),
                place.taggedSports(),
                place.openingHours(),
                place.suggestedSchedule().orElse(null),
                suggestedSports
        );
    }
}
