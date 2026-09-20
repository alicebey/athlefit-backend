package com.athlefit.backend.repository;

import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.BookingActor;
import com.athlefit.backend.model.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {
            "user", "court", "court.venueSport", "court.venueSport.sport",
            "court.venueSport.venue", "court.venueSport.venue.openDays"
    })
    List<Booking> findAllByUserIdOrderByStartAtDesc(UUID userId);

    @EntityGraph(attributePaths = {
            "user", "court", "court.venueSport", "court.venueSport.sport",
            "court.venueSport.venue", "court.venueSport.venue.openDays"
    })
    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {
            "user", "court", "court.venueSport", "court.venueSport.sport",
            "court.venueSport.venue", "court.venueSport.venue.openDays"
    })
    Optional<Booking> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {
            "user", "court", "court.venueSport", "court.venueSport.sport",
            "court.venueSport.venue", "court.venueSport.venue.openDays"
    })
    @Query("""
            SELECT booking
            FROM Booking booking
            WHERE booking.court.venueSport.venue.id = :venueId
              AND booking.endAt >= :from
            ORDER BY booking.startAt ASC
            """)
    List<Booking> findVenueBookingsEndingAfter(
            @Param("venueId") UUID venueId,
            @Param("from") OffsetDateTime from
    );

    /** Bookings that hold a court of the given venue sport during [from, to). */
    @Query("""
            SELECT booking
            FROM Booking booking
            WHERE booking.court.venueSport.id = :venueSportId
              AND booking.startAt < :to
              AND booking.endAt > :from
              AND booking.status IN :statuses
            """)
    List<Booking> findOverlapping(
            @Param("venueSportId") UUID venueSportId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    boolean existsByCourtIdAndStatusInAndEndAtAfter(
            UUID courtId,
            Collection<BookingStatus> statuses,
            OffsetDateTime now
    );

    /** Releases courts held by unpaid bookings whose payment window has passed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Booking booking
            SET booking.status = :expired,
                booking.cancelledBy = :system,
                booking.cancelledAt = :now,
                booking.cancellationReason = 'Payment was not received in time'
            WHERE booking.status = :pending
              AND booking.paymentDeadline <= :now
            """)
    int expireOverduePayments(
            @Param("pending") BookingStatus pending,
            @Param("expired") BookingStatus expired,
            @Param("system") BookingActor system,
            @Param("now") OffsetDateTime now
    );
}
