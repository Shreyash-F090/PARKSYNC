package com.parksync.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "support_messages", indexes = @Index(name = "ix_support_message_ticket", columnList = "ticket_id,created_at"))
public class SupportMessage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private SupportTicket ticket;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;
    @Column(name = "author_role", nullable = false, length = 20)
    private String authorRole;
    @Column(nullable = false, length = 3000)
    private String message;

    public SupportTicket getTicket() { return ticket; }
    public void setTicket(SupportTicket ticket) { this.ticket = ticket; }
    public AppUser getAuthor() { return author; }
    public void setAuthor(AppUser author) { this.author = author; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String authorRole) { this.authorRole = authorRole; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}