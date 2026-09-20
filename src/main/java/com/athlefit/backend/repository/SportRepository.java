package com.athlefit.backend.repository;

import com.athlefit.backend.model.Sport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SportRepository extends JpaRepository<Sport, UUID> {
    List<Sport> findAllByOrderByNameAsc();

    Optional<Sport> findBySlug(String slug);
}
