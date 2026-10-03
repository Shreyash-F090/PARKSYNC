package com.parksync.backend.controller;

import com.parksync.backend.dto.ApiDtos.LocationDto;
import com.parksync.backend.dto.ApiDtos.SlotDto;
import com.parksync.backend.model.VehicleType;
import com.parksync.backend.service.LocationService;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/v1/locations")
public class LocationController {
    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    public List<LocationDto> search(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) VehicleType vehicleType,
                                    @RequestParam(required = false) Boolean availableOnly,
                                    @RequestParam(required = false) BigDecimal maxHourlyRate,
                                    @RequestParam(required = false) String sort) {
        return locations.search(q, category, vehicleType, availableOnly, maxHourlyRate, sort);
    }

    @GetMapping("/{id}")
    public LocationDto get(@PathVariable Long id) {
        return locations.getActive(id);
    }

    @GetMapping("/{id}/slots")
    public List<SlotDto> slots(@PathVariable Long id) {
        return locations.listSlots(id, false);
    }
}