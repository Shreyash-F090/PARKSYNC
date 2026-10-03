package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.NotificationDto;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.AppUser;
import com.parksync.backend.model.NotificationRecord;
import com.parksync.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional
    public void create(AppUser owner, String title, String message) {
        NotificationRecord record = new NotificationRecord();
        record.setOwner(owner);
        record.setTitle(title);
        record.setMessage(message);
        notifications.save(record);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long ownerId) {
        return notifications.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(ApiMapper::notification).toList();
    }

    @Transactional
    public void markRead(Long ownerId, Long id) {
        NotificationRecord record = notifications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        if (record.getReadAt() == null) record.setReadAt(Instant.now());
    }
}