package com.parksync.backend.service;

import com.parksync.backend.model.AppUser;
import com.parksync.backend.model.AuditLog;
import com.parksync.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository logs;

    public AuditLogService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public void record(AppUser actor, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        logs.save(log);
    }
}