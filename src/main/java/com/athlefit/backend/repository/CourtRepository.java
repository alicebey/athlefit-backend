package com.athlefit.backend.repository;

import com.athlefit.backend.model.BookingStatus;
import com.athlefit.backend.model.Court;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {

    @Query("""
            SELECT court
            FROM Court court
            WHERE court.venueSport.id = :venueSportId
              AND court.active = true
              AND NOT EXISTS (
                  SELECT booking.id
                  FROM Booking booking
                  WHERE booking.court.id = court.id
                    AND booking.status IN :statuses
                    AND booking.startAt < :requestedEnd
                    AND booking.endAt > :requestedStart
              )
            ORDER BY court.name
            """)
    List<Court> findAvailable(
            @Param("venueSportId") UUID venueSportId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("requestedStart") OffsetDateTime requestedStart,
            @Param("requestedEnd") OffsetDateTime requestedEnd,
            Pageable pageable
    );

    long countByVenueSportIdAndActiveTrue(UUID venueSportId);

    List<Court> findAllByVenueSportIdAndActiveTrue(UUID venueSportId);

    List<Court> findAllByVenueSportId(UUID venueSportId);
}
