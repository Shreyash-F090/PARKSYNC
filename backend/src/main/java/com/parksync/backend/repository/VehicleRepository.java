package com.parksync.backend.repository;

import com.parksync.backend.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Vehicle> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByRegistrationIgnoreCase(String registration);
    boolean existsByRegistrationIgnoreCaseAndIdNot(String registration, Long id);
}