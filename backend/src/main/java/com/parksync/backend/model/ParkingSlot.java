package com.parksync.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "parking_slots", uniqueConstraints = @UniqueConstraint(name = "uk_slot_location_code", columnNames = {"location_id", "code"}))
public class ParkingSlot extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private ParkingLocation location;
    @Column(nullable = false, length = 20)
    private String code;
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SlotStatus status = SlotStatus.AVAILABLE;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "slot_vehicle_types", joinColumns = @JoinColumn(name = "slot_id"))
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private Set<VehicleType> vehicleTypes = new LinkedHashSet<>();
    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    public ParkingLocation getLocation() { return location; }
    public void setLocation(ParkingLocation location) { this.location = location; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
    public Set<VehicleType> getVehicleTypes() { return vehicleTypes; }
    public void setVehicleTypes(Set<VehicleType> vehicleTypes) { this.vehicleTypes = vehicleTypes; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
}