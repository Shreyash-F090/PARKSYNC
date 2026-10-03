package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.SupportInput;
import com.parksync.backend.dto.ApiDtos.SupportReplyInput;
import com.parksync.backend.dto.ApiDtos.SupportTicketDto;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.*;
import com.parksync.backend.repository.SupportMessageRepository;
import com.parksync.backend.repository.SupportTicketRepository;
import com.parksync.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
public class SupportService {
    private final SupportTicketRepository tickets;
    private final SupportMessageRepository messages;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditLogService audit;

    public SupportService(SupportTicketRepository tickets, SupportMessageRepository messages,
                         UserRepository users, NotificationService notifications, AuditLogService audit) {
        this.tickets = tickets;
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> mine(Long ownerId) {
        return tickets.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> all() {
        return tickets.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
    }

    @Transactional
    public SupportTicketDto create(Long ownerId, SupportInput input) {
        AppUser owner = findUser(ownerId);
        SupportTicket ticket = new SupportTicket();
        ticket.setOwner(owner);
        ticket.setCategory(input.category().trim().toUpperCase(Locale.ROOT));
        ticket.setSubject(input.subject().trim());
        ticket.setDescription(input.description().trim());
        ticket.setStatus("OPEN");
        ticket = tickets.save(ticket);
        addMessage(ticket, owner, input.description().trim());
        notifications.create(owner, "Support request received",
                "Request #" + ticket.getId() + " is in the support queue.");
        audit.record(owner, "SUPPORT_CREATED", "SUPPORT_TICKET", ticket.getId(), ticket.getSubject());
        return map(ticket);
    }

    @Transactional
    public SupportTicketDto reply(Long adminId, Long ticketId, SupportReplyInput input) {
        SupportTicket ticket = tickets.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Support request not found."));
        AppUser admin = findUser(adminId);
        ticket.setStatus(input.status().trim().toUpperCase(Locale.ROOT));
        addMessage(ticket, admin, input.response().trim());
        notifications.create(ticket.getOwner(), "Support team replied",
                "There is a new update on your request: " + ticket.getSubject());
        audit.record(admin, "SUPPORT_REPLIED", "SUPPORT_TICKET", ticket.getId(),
                "Status set to " + ticket.getStatus());
        return map(ticket);
    }

    private SupportTicketDto map(SupportTicket ticket) {
        return ApiMapper.ticket(ticket, messages.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    private void addMessage(SupportTicket ticket, AppUser author, String body) {
        SupportMessage message = new SupportMessage();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setAuthorRole(author.getRole().name());
        message.setMessage(body);
        messages.save(message);
    }

    private AppUser findUser(Long id) {
        return users.findById(id).filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
    }
}