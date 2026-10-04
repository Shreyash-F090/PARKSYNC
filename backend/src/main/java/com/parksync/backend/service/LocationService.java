package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.LocationDto;
import com.parksync.backend.dto.ApiDtos.LocationInput;
import com.parksync.backend.dto.ApiDtos.SlotDto;
import com.parksync.backend.dto.ApiDtos.SlotInput;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.*;
import com.parksync.backend.repository.BookingRepository;
import com.parksync.backend.repository.LocationRepository;
import com.parksync.backend.repository.ParkingSlotRepository;
import com.parksync.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LocationService {
    private final LocationRepository locations;
    private final ParkingSlotRepository slots;
    private final BookingRepository bookings;
    private final AuditLogService audit;
    private final UserRepository users;

    public LocationService(LocationRepository locations, ParkingSlotRepository slots,
                           BookingRepository bookings, AuditLogService audit, UserRepository users) {
        this.locations = locations;
        this.slots = slots;
        this.bookings = bookings;
        this.audit = audit;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<LocationDto> search(String query, String category, VehicleType vehicleType,
                                    Boolean availableOnly, BigDecimal maxHourlyRate, String sort) {
        String normalizedQuery = clean(query);
        return locations.findAllByActiveTrueOrderByNameAsc().stream()
                .filter(location -> matches(location, normalizedQuery, category, vehicleType, maxHourlyRate))
                .map(this::mapLocation)
                .filter(location -> !Boolean.TRUE.equals(availableOnly) || location.availableSlots() > 0)
                .sorted(comparator(sort))
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationDto getActive(Long id) {
        ParkingLocation location = locations.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        return mapLocation(location);
    }

    @Transactional(readOnly = true)
    public LocationDto getAny(Long id) {
        return locations.findById(id).map(this::mapLocation)
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
    }

    @Transactional(readOnly = true)
    public List<LocationDto> listAdmin() {
        return locations.findAllByOrderByNameAsc().stream().map(this::mapLocation).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotDto> listSlots(Long locationId, boolean allowInactive) {
        ParkingLocation location = allowInactive
                ? locations.findById(locationId).orElseThrow(() -> ApiException.notFound("Parking facility not found."))
                : locations.findByIdAndActiveTrue(locationId).orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        return slots.findAllByLocationIdOrderByCodeAsc(location.getId()).stream()
                .map(ApiMapper::slot).toList();
    }

    @Transactional
    public LocationDto createLocation(Long actorId, LocationInput input) {
        ParkingLocation location = new ParkingLocation();
        apply(location, input);
        location.setActive(true);
        location.setDemoData(false);
        location = locations.save(location);
        audit.record(actor(actorId), "LOCATION_CREATED", "LOCATION", location.getId(), "Created facility " + location.getName());
        return mapLocation(location);
    }

    @Transactional
    public LocationDto updateLocation(Long actorId, Long id, LocationInput input) {
        ParkingLocation location = locations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        apply(location, input);
        location = locations.save(location);
        audit.record(actor(actorId), "LOCATION_UPDATED", "LOCATION", location.getId(), "Updated facility details");
        return mapLocation(location);
    }

    @Transactional
    public void setLocationActive(Long actorId, Long id, boolean active) {
        ParkingLocation location = locations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        if (!active && bookings.existsByLocationIdAndStatusIn(id, List.of(BookingStatus.ACTIVE))) {
            throw ApiException.conflict("A facility with an active vehicle cannot be deactivated.");
        }
        location.setActive(active);
        audit.record(actor(actorId), active ? "LOCATION_ACTIVATED" : "LOCATION_DEACTIVATED",
                "LOCATION", id, "Facility active set to " + active);
    }

    @Transactional
    public void deactivateLocation(Long actorId, Long id) {
        setLocationActive(actorId, id, false);
    }

    @Transactional
    public SlotDto createSlot(Long actorId, Long locationId, SlotInput input) {
        ParkingLocation location = locations.findById(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        ParkingSlot slot = new ParkingSlot();
        slot.setLocation(location);
        apply(slot, input);
        slot = slots.save(slot);
        audit.record(actor(actorId), "SLOT_CREATED", "SLOT", slot.getId(), "Added slot " + slot.getCode());
        return ApiMapper.slot(slot);
    }

    @Transactional
    public SlotDto updateSlot(Long actorId, Long slotId, SlotInput input) {
        ParkingSlot slot = slots.findById(slotId)
                .orElseThrow(() -> ApiException.notFound("Parking slot not found."));
        if (bookings.existsBySlotIdAndStatusIn(slotId, List.of(BookingStatus.ACTIVE))
                && input.status() != SlotStatus.OCCUPIED) {
            throw ApiException.conflict("An active booking is using this slot.");
        }
        apply(slot, input);
        slot = slots.save(slot);
        audit.record(actor(actorId), "SLOT_UPDATED", "SLOT", slot.getId(), "Updated slot " + slot.getCode());
        return ApiMapper.slot(slot);
    }

    private LocationDto mapLocation(ParkingLocation location) {
        return ApiMapper.location(location, slots.findAllByLocationIdOrderByCodeAsc(location.getId()));
    }

    private AppUser actor(Long actorId) {
        return users.findById(actorId).orElseThrow(() -> ApiException.notFound("Administrator account not found."));
    }

    private boolean matches(ParkingLocation location, String query, String category,
                            VehicleType type, BigDecimal maxHourlyRate) {
        if (query != null && !(location.getName().toLowerCase(Locale.ROOT).contains(query)
                || location.getAddress().toLowerCase(Locale.ROOT).contains(query)
                || location.getArea().toLowerCase(Locale.ROOT).contains(query))) return false;
        if (category != null && !location.getCategory().equalsIgnoreCase(category)) return false;
        if (type != null && !location.getSupportedVehicleTypes().contains(type)) return false;
        return maxHourlyRate == null || location.getHourlyRate().compareTo(maxHourlyRate) <= 0;
    }

    private Comparator<LocationDto> comparator(String sort) {
        if ("price".equalsIgnoreCase(sort)) return Comparator.comparing(LocationDto::hourlyRate);
        if ("availability".equalsIgnoreCase(sort)) return Comparator.comparingInt(LocationDto::availableSlots).reversed();
        return Comparator.comparing(LocationDto::name, String.CASE_INSENSITIVE_ORDER);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeMapUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (!trimmed.regionMatches(true, 0, "http://", 0, 7)
                && !trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            throw ApiException.badRequest("Map URL must start with http:// or https://.");
        }
        return trimmed;
    }

    private static void apply(ParkingLocation location, LocationInput input) {
        location.setName(input.name().trim());
        location.setAddress(input.address().trim());
        location.setArea(input.area().trim());
        location.setCategory(input.category().toUpperCase(Locale.ROOT));
        location.setDescription(input.description().trim());
        location.setOperatingHours(input.operatingHours().trim());
        location.setSupportedVehicleTypes(Set.copyOf(input.supportedVehicleTypes()));
        location.setHourlyRate(input.hourlyRate());
        location.setMapUrl(safeMapUrl(input.mapUrl()));
    }

    private static void apply(ParkingSlot slot, SlotInput input) {
        slot.setCode(input.code().trim().toUpperCase(Locale.ROOT));
        slot.setStatus(input.status());
        slot.setVehicleTypes(Set.copyOf(input.vehicleTypes()));
        slot.setHourlyRate(input.hourlyRate());
    }
}