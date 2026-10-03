package com.parksync.backend.controller;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.security.AppPrincipal;
import com.parksync.backend.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/bookings")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/quote")
    public BookingQuote quote(@AuthenticationPrincipal AppPrincipal principal,
                              @Valid @RequestBody BookingQuoteInput input) {
        return bookings.quote(principal.id(), input);
    }

    @GetMapping
    public List<BookingDto> mine(@AuthenticationPrincipal AppPrincipal principal,
                                 @RequestParam(required = false) String status) {
        return bookings.myBookings(principal.id(), status);
    }

    @PostMapping
    public ResponseEntity<BookingDto> create(@AuthenticationPrincipal AppPrincipal principal,
                                              @Valid @RequestBody BookingInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings.create(principal.id(), input));
    }

    @GetMapping("/{id}")
    public BookingDto get(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id) {
        return bookings.myBooking(principal.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@AuthenticationPrincipal AppPrincipal principal, @PathVariable Long id) {
        return bookings.cancel(principal.id(), id);
    }
}