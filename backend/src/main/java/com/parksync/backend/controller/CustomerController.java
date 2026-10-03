package com.parksync.backend.controller;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.security.AppPrincipal;
import com.parksync.backend.service.AccountService;
import com.parksync.backend.service.BookingService;
import com.parksync.backend.service.NotificationService;
import com.parksync.backend.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1")
public class CustomerController {
    private final AccountService accounts;
    private final BookingService bookings;
    private final NotificationService notifications;
    private final SupportService support;

    public CustomerController(AccountService accounts, BookingService bookings,
                              NotificationService notifications, SupportService support) {
        this.accounts = accounts;
        this.bookings = bookings;
        this.notifications = notifications;
        this.support = support;
    }

    @PatchMapping("/profile")
    public UserDto updateProfile(@AuthenticationPrincipal AppPrincipal principal,
                                 @Valid @RequestBody ProfileUpdate input) {
        return accounts.updateProfile(principal.id(), input);
    }

    @PatchMapping("/profile/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppPrincipal principal,
                                               @Valid @RequestBody PasswordChangeInput input) {
        accounts.changePassword(principal.id(), input);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard")
    public CustomerDashboard dashboard(@AuthenticationPrincipal AppPrincipal principal) {
        return accounts.dashboard(principal.id());
    }

    @GetMapping("/vehicles")
    public List<VehicleDto> vehicles(@AuthenticationPrincipal AppPrincipal principal) {
        return accounts.vehicles(principal.id());
    }

    @PostMapping("/vehicles")
    public ResponseEntity<VehicleDto> createVehicle(@AuthenticationPrincipal AppPrincipal principal,
                                                     @Valid @RequestBody VehicleInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.createVehicle(principal.id(), input));
    }

    @PutMapping("/vehicles/{id}")
    public VehicleDto updateVehicle(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id,
                                    @Valid @RequestBody VehicleInput input) {
        return accounts.updateVehicle(principal.id(), id, input);
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<Void> deleteVehicle(@AuthenticationPrincipal AppPrincipal principal,
                                               @PathVariable Long id) {
        accounts.deleteVehicle(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payments")
    public List<PaymentDto> payments(@AuthenticationPrincipal AppPrincipal principal) {
        return bookings.myPayments(principal.id());
    }

    @PostMapping("/payments/demo")
    public ResponseEntity<PaymentDto> demoPayment(@AuthenticationPrincipal AppPrincipal principal,
                                                   @Valid @RequestBody DemoPaymentInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings.demoPayment(principal.id(), input));
    }

    @GetMapping("/notifications")
    public List<NotificationDto> notifications(@AuthenticationPrincipal AppPrincipal principal) {
        return notifications.list(principal.id());
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(@AuthenticationPrincipal AppPrincipal principal,
                                                  @PathVariable Long id) {
        notifications.markRead(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/support")
    public List<SupportTicketDto> support(@AuthenticationPrincipal AppPrincipal principal) {
        return support.mine(principal.id());
    }

    @PostMapping("/support")
    public ResponseEntity<SupportTicketDto> createSupportTicket(@AuthenticationPrincipal AppPrincipal principal,
                                                                 @Valid @RequestBody SupportInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(support.create(principal.id(), input));
    }
}