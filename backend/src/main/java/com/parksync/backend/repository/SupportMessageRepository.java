package com.parksync.backend.repository;

import com.parksync.backend.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}