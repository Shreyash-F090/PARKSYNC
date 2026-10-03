package com.parksync.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "support_tickets", indexes = @Index(name = "ix_support_owner_created", columnList = "owner_id,created_at"))
public class SupportTicket extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(nullable = false, length = 40)
    private String category;
    @Column(nullable = false, length = 160)
    private String subject;
    @Column(nullable = false, length = 3000)
    private String description;
    @Column(nullable = false, length = 24)
    private String status = "OPEN";

    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}