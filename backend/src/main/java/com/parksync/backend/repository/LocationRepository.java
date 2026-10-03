package com.parksync.backend.repository;

import com.parksync.backend.model.ParkingLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<ParkingLocation, Long> {
    List<ParkingLocation> findAllByActiveTrueOrderByNameAsc();
    List<ParkingLocation> findAllByOrderByNameAsc();
    Optional<ParkingLocation> findByIdAndActiveTrue(Long id);
}