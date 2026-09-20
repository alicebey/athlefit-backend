package com.athlefit.backend.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoapifyPlaceTest {

    @Test
    void parsesAWeeklyScheduleWithOneTimeRange() {
        GeoapifyPlace place = place("Mo-Su 08:00-22:00");

        GeoapifyPlace.OpeningSchedule schedule = place.suggestedSchedule().orElseThrow();

        assertEquals(List.of(DayOfWeek.values()), schedule.openDays());
        assertEquals(LocalTime.of(8, 0), schedule.openTime());
        assertEquals(LocalTime.of(22, 0), schedule.closeTime());
    }

    @Test
    void leavesDifferentDailyHoursForAdminConfirmation() {
        GeoapifyPlace place = place("Mo-Fr 08:00-22:00; Sa-Su 09:00-20:00");

        assertTrue(place.suggestedSchedule().isEmpty());
    }

    private GeoapifyPlace place(String openingHours) {
        return new GeoapifyPlace(
                "place-id", "Venue", "Jakarta", null, null,
                java.util.Set.of(), java.util.Set.of(), openingHours, null
        );
    }
}
