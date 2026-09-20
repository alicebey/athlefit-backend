package com.athlefit.backend.service;

import com.athlefit.backend.dto.request.CreateGeoapifyVenueRequest;
import com.athlefit.backend.dto.response.VenueResponse;
import com.athlefit.backend.model.Sport;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueSport;
import com.athlefit.backend.repository.CourtRepository;
import com.athlefit.backend.repository.SportRepository;
import com.athlefit.backend.repository.VenueRepository;
import com.athlefit.backend.repository.VenueSportRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VenueServiceTest {

    @Test
    void createsTheConfiguredNumberOfCourts() {
        VenueRepository venueRepository = mock(VenueRepository.class);
        VenueSportRepository venueSportRepository = mock(VenueSportRepository.class);
        SportRepository sportRepository = mock(SportRepository.class);
        CourtRepository courtRepository = mock(CourtRepository.class);
        Sport sport = mock(Sport.class);
        when(sport.getSlug()).thenReturn("badminton");
        when(sportRepository.findBySlug("badminton")).thenReturn(Optional.of(sport));
        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(venueSportRepository.save(any(VenueSport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(courtRepository.countByVenueSportIdAndActiveTrue(nullable(java.util.UUID.class)))
                .thenReturn(3L);
        VenueService service = new VenueService(
                venueRepository,
                venueSportRepository,
                sportRepository,
                courtRepository
        );

        List<VenueResponse> result = service.createGeoapifyVenue(
                geoapifyPlace(),
                new GeoapifyPlace.OpeningSchedule(
                        List.of(DayOfWeek.MONDAY),
                        LocalTime.of(8, 0),
                        LocalTime.of(22, 0)
                ),
                List.of(new CreateGeoapifyVenueRequest.SportConfiguration(
                        "badminton",
                        new BigDecimal("75000.00"),
                        3
                ))
        );

        verify(courtRepository).saveAll(argThat(courts ->
                StreamSupport.stream(courts.spliterator(), false).count() == 3
        ));
        assertEquals(3, result.get(0).courtCount());
        assertEquals(new BigDecimal("75000.00"), result.get(0).hourlyRate());
    }

    private GeoapifyPlace geoapifyPlace() {
        return new GeoapifyPlace(
                "place-id",
                "Badminton Arena",
                "Jakarta",
                new BigDecimal("-6.200000"),
                new BigDecimal("106.800000"),
                Set.of("sport.sports_centre"),
                Set.of("badminton"),
                null,
                null
        );
    }
}
