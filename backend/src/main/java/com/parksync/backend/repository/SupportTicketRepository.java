package com.parksync.backend.repository;

import com.parksync.backend.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
    Optional<SupportTicket> findByIdAndOwnerId(Long id, Long ownerId);
}