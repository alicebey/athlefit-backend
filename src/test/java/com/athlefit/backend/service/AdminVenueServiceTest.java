package com.athlefit.backend.service;

import com.athlefit.backend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AdminVenueServiceTest {

    @Test
    void reportsWhetherTheAuthenticatedUserIsAnAdmin() {
        AdminVenueService service = new AdminVenueService(
                mock(GeoapifyClient.class),
                mock(VenueService.class),
                "first-admin, second-admin"
        );

        assertTrue(service.isAdmin(new AuthenticatedUser("second-admin", null, null)));
        assertFalse(service.isAdmin(new AuthenticatedUser("regular-user", null, null)));
    }

    @Test
    void suggestsSportsFromGeoapifyCategoriesTagsAndPlaceName() {
        AdminVenueService service = new AdminVenueService(
                mock(GeoapifyClient.class),
                mock(VenueService.class),
                "admin-uid"
        );
        GeoapifyPlace place = new GeoapifyPlace(
                "place-id",
                "Jakarta Badminton Center",
                "Jakarta",
                null,
                null,
                Set.of("sport.golf_course"),
                Set.of("tennis"),
                null,
                null
        );

        assertEquals(
                Set.of("golf", "tennis", "badminton"),
                new HashSet<>(service.suggestSports(place))
        );
    }
}
