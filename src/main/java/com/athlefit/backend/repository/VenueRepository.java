package com.athlefit.backend.repository;

import com.athlefit.backend.model.Venue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    boolean existsByProviderPlaceId(String providerPlaceId);

    boolean existsByOwnerFirebaseUid(String firebaseUid);

    @EntityGraph(attributePaths = {"openDays", "owner"})
    List<Venue> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"openDays", "owner"})
    List<Venue> findAllByOwnerFirebaseUidOrderByNameAsc(String firebaseUid);

    @EntityGraph(attributePaths = {"openDays", "owner"})
    Optional<Venue> findWithOwnerById(UUID id);
}
