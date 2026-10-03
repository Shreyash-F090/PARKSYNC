package com.parksync.backend.repository;

import com.parksync.backend.model.Booking;
import com.parksync.backend.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<Booking> findTop10ByOrderByCreatedAtDesc();
    Optional<Booking> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<Booking> findByReferenceIgnoreCase(String reference);
    long countByStatus(BookingStatus status);
    long countByOwnerId(Long ownerId);
    boolean existsByVehicleIdAndStatusIn(Long vehicleId, List<BookingStatus> statuses);
    boolean existsByVehicleId(Long vehicleId);
    boolean existsBySlotIdAndStatusIn(Long slotId, List<BookingStatus> statuses);

    @Query("""
        select count(b) > 0 from Booking b
        where b.location.id = :locationId and b.status in :statuses
        """)
    boolean existsByLocationIdAndStatusIn(@Param("locationId") Long locationId,
                                          @Param("statuses") List<BookingStatus> statuses);

    @Query("""
        select count(b) > 0 from Booking b
        where b.slot.id = :slotId
          and b.status <> :cancelled
          and b.startAt < :requestedEnd
          and b.endAt > :requestedStart
        """)
    boolean existsOverlap(@Param("slotId") Long slotId,
                          @Param("cancelled") BookingStatus cancelled,
                          @Param("requestedStart") Instant requestedStart,
                          @Param("requestedEnd") Instant requestedEnd);

    @Query("""
        select b from Booking b
        where (:status is null or b.status = :status)
          and (:locationId is null or b.location.id = :locationId)
          and (:query is null or lower(b.reference) like lower(concat('%', :query, '%'))
               or lower(b.owner.name) like lower(concat('%', :query, '%'))
               or lower(b.vehicle.registration) like lower(concat('%', :query, '%')))
        order by b.createdAt desc
        """)
    List<Booking> searchAdmin(@Param("query") String query,
                              @Param("status") BookingStatus status,
                              @Param("locationId") Long locationId);
}