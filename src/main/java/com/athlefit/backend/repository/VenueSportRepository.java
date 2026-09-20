package com.athlefit.backend.repository;

import com.athlefit.backend.model.VenueSport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueSportRepository extends JpaRepository<VenueSport, UUID> {

    @EntityGraph(attributePaths = {"venue", "venue.openDays", "sport"})
    List<VenueSport> findAllByVenueActiveTrue();

    @EntityGraph(attributePaths = {"venue", "venue.openDays", "sport"})
    List<VenueSport> findAllByVenueActiveTrueAndSportSlug(String sportSlug);

    @EntityGraph(attributePaths = {"venue", "venue.openDays", "sport"})
    Optional<VenueSport> findByVenueIdAndSportSlug(UUID venueId, String sportSlug);

    @EntityGraph(attributePaths = {"venue", "venue.openDays", "sport"})
    Optional<VenueSport> findFirstByVenueIdOrderBySportName(UUID venueId);

    @EntityGraph(attributePaths = {"venue", "venue.openDays", "sport"})
    List<VenueSport> findAllByVenueIdOrderBySportName(UUID venueId);
}
