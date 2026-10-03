package com.parksync.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "parking_locations")
public class ParkingLocation extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 300)
    private String address;
    @Column(nullable = false, length = 100)
    private String area;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(name = "operating_hours", nullable = false, length = 100)
    private String operatingHours;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "location_vehicle_types", joinColumns = @JoinColumn(name = "location_id"))
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private Set<VehicleType> supportedVehicleTypes = new LinkedHashSet<>();
    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;
    @Column(name = "map_url", length = 500)
    private String mapUrl;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "demo_data", nullable = false)
    private boolean demoData;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }
    public Set<VehicleType> getSupportedVehicleTypes() { return supportedVehicleTypes; }
    public void setSupportedVehicleTypes(Set<VehicleType> supportedVehicleTypes) { this.supportedVehicleTypes = supportedVehicleTypes; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public String getMapUrl() { return mapUrl; }
    public void setMapUrl(String mapUrl) { this.mapUrl = mapUrl; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isDemoData() { return demoData; }
    public void setDemoData(boolean demoData) { this.demoData = demoData; }
}