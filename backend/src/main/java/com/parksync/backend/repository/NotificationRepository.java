package com.parksync.backend.repository;

import com.parksync.backend.model.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationRecord, Long> {
    List<NotificationRecord> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<NotificationRecord> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerIdAndReadAtIsNull(Long ownerId);
}