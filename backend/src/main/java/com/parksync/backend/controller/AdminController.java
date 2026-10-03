package com.parksync.backend.controller;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.security.AppPrincipal;
import com.parksync.backend.service.AdminService;
import com.parksync.backend.service.BookingService;
import com.parksync.backend.service.LocationService;
import com.parksync.backend.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {
    private final AdminService admin;
    private final BookingService bookings;
    private final LocationService locations;
    private final SupportService support;

    public AdminController(AdminService admin, BookingService bookings,
                           LocationService locations, SupportService support) {
        this.admin = admin;
        this.bookings = bookings;
        this.locations = locations;
        this.support = support;
    }

    @GetMapping("/overview")
    public AdminOverview overview() {
        return admin.overview();
    }

    @GetMapping("/users")
    public List<UserDto> users(@RequestParam(required = false) String q) {
        return admin.listUsers(q);
    }

    @PatchMapping("/users/{id}/active")
    public UserDto setUserActive(@AuthenticationPrincipal AppPrincipal principal,
                                 @PathVariable Long id, @Valid @RequestBody ActiveInput input) {
        return admin.setUserActive(principal.id(), id, input.active());
    }

    @GetMapping("/locations")
    public List<LocationDto> locations() {
        return locations.listAdmin();
    }

    @PostMapping("/locations")
    public ResponseEntity<LocationDto> createLocation(@AuthenticationPrincipal AppPrincipal principal,
                                                       @Valid @RequestBody LocationInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locations.createLocation(principal.id(), input));
    }

    @PutMapping("/locations/{id}")
    public LocationDto updateLocation(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id,
                                      @Valid @RequestBody LocationInput input) {
        return locations.updateLocation(principal.id(), id, input);
    }

    @DeleteMapping("/locations/{id}")
    public ResponseEntity<Void> deactivateLocation(@AuthenticationPrincipal AppPrincipal principal,
                                                    @PathVariable Long id) {
        locations.deactivateLocation(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/locations/{id}/active")
    public ResponseEntity<Void> setLocationActive(@AuthenticationPrincipal AppPrincipal principal,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody ActiveInput input) {
        locations.setLocationActive(principal.id(), id, input.active());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/locations/{id}/slots")
    public List<SlotDto> slots(@PathVariable Long id) {
        return locations.listSlots(id, true);
    }

    @PostMapping("/locations/{id}/slots")
    public ResponseEntity<SlotDto> createSlot(@AuthenticationPrincipal AppPrincipal principal,
                                               @PathVariable Long id, @Valid @RequestBody SlotInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locations.createSlot(principal.id(), id, input));
    }

    @PutMapping("/slots/{id}")
    public SlotDto updateSlot(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id,
                              @Valid @RequestBody SlotInput input) {
        return locations.updateSlot(principal.id(), id, input);
    }

    @GetMapping("/bookings")
    public List<BookingDto> bookings(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) Long locationId) {
        return bookings.adminBookings(q, status, locationId);
    }

    @PostMapping("/bookings/{id}/entry")
    public BookingDto entry(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id) {
        return bookings.recordEntry(principal.id(), id);
    }

    @PostMapping("/bookings/{id}/exit")
    public BookingDto exit(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id) {
        return bookings.recordExit(principal.id(), id);
    }

    @GetMapping("/payments")
    public List<PaymentDto> payments(@RequestParam(required = false) String status) {
        return bookings.allPayments(status);
    }

    @GetMapping("/reports")
    public AdminOverview reports(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return admin.reports(from, to);
    }

    @GetMapping("/support")
    public List<SupportTicketDto> support() {
        return support.all();
    }

    @PostMapping("/support/{id}/reply")
    public SupportTicketDto reply(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody SupportReplyInput input) {
        return support.reply(principal.id(), id, input);
    }
}