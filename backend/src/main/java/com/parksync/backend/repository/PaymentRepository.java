package com.parksync.backend.repository;

import com.parksync.backend.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<PaymentRecord> findAllByOrderByCreatedAtDesc();
    List<PaymentRecord> findAllByStatusOrderByCreatedAtDesc(String status);
    boolean existsByBookingId(Long bookingId);
    @Query("select coalesce(sum(p.amount), 0) from PaymentRecord p")
    BigDecimal sumRecordedAmounts();
}