package com.parksync.backend.dto;

import com.parksync.backend.model.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ApiDtos {
    private ApiDtos() { }

    public record UserDto(Long id, String name, String email, String phone,
                          String role, boolean active, Instant createdAt) { }
    public record AuthResponse(String token, UserDto user) { }

    public record RegistrationInput(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 7, max = 30) String phone,
            @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{10,72}$",
                    message = "Use 10–72 characters with upper and lower case letters, a number, and a symbol.") String password,
            @AssertTrue(message = "Accept the terms to create an account.") boolean termsAccepted) { }

    public record LoginInput(@NotBlank @Email String email, @NotBlank String password) { }

    public record ProfileUpdate(@NotBlank @Size(min = 2, max = 120) String name,
                                @NotBlank @Size(min = 7, max = 30) String phone) { }

    public record PasswordChangeInput(@NotBlank String currentPassword,
                                      @NotBlank @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{10,72}$",
                                              message = "Use 10–72 characters with upper and lower case letters, a number, and a symbol.")
                                      String newPassword) { }

    public record VehicleInput(
            @NotBlank @Size(min = 4, max = 20) @Pattern(regexp = "^[A-Za-z0-9 -]+$") String registration,
            @NotNull VehicleType type,
            @NotBlank @Size(max = 80) String brand,
            @NotBlank @Size(max = 80) String model,
            @Size(max = 50) String color) { }

    public record VehicleDto(Long id, String registration, VehicleType type,
                             String brand, String model, String color) { }

    public record LocationInput(
            @NotBlank @Size(min = 2, max = 150) String name,
            @NotBlank @Size(min = 5, max = 300) String address,
            @NotBlank @Size(max = 100) String area,
            @NotBlank @Pattern(regexp = "MALL|PUBLIC|STANDALONE|OTHER") String category,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String operatingHours,
            @NotEmpty Set<VehicleType> supportedVehicleTypes,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal hourlyRate,
            @Size(max = 500) @Pattern(regexp = "(?i)https?://\\S+",
                    message = "Map URL must start with http:// or https://.") String mapUrl) { }

    public record LocationDto(Long id, String name, String address, String area, String category,
                              String description, String operatingHours, Set<VehicleType> supportedVehicleTypes,
                              BigDecimal hourlyRate, int totalSlots, int availableSlots, int occupiedSlots,
                              boolean active, boolean demoData, String mapUrl) { }

    public record SlotInput(
            @NotBlank @Size(max = 20) String code,
            @NotNull SlotStatus status,
            @NotEmpty Set<VehicleType> vehicleTypes,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal hourlyRate) { }

    public record SlotDto(Long id, Long locationId, String code, SlotStatus status,
                          Set<VehicleType> vehicleTypes, BigDecimal hourlyRate) { }

    public record BookingQuoteInput(@NotNull Long locationId, @NotNull Long vehicleId,
                                    @NotNull Instant startAt, @NotNull Instant endAt) { }

    public record BookingQuote(BigDecimal estimatedFee, int billableHours, String currency,
                               List<SlotDto> slots, String pricingRule) { }

    public record BookingInput(@NotNull Long locationId, @NotNull Long vehicleId,
                               @NotNull Long slotId, @NotNull Instant startAt,
                               @NotNull Instant endAt) { }

    public record BookingDto(Long id, String reference, Long locationId, String locationName,
                             Long slotId, String slotCode, String vehicleRegistration, String customerName,
                             Instant startAt, Instant endAt, Instant entryAt, Instant exitAt,
                             BookingStatus status, BigDecimal estimatedFee, BigDecimal finalFee,
                             PaymentStatus paymentStatus) { }

    public record CustomerDashboard(List<VehicleDto> vehicles, List<BookingDto> recentBookings,
                                    List<NotificationDto> notifications, int activeBookingCount) { }

    public record NotificationDto(Long id, String title, String message, Instant createdAt, boolean read) { }

    public record SupportInput(@NotBlank @Size(max = 40) String category,
                               @NotBlank @Size(min = 2, max = 160) String subject,
                               @NotBlank @Size(min = 10, max = 3000) String description) { }

    public record SupportReplyInput(@NotBlank @Size(max = 3000) String response,
                                    @NotBlank @Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status) { }

    public record SupportMessageDto(Long id, String authorName, String authorRole,
                                    String message, Instant createdAt) { }

    public record SupportTicketDto(Long id, String customerName, String category, String subject,
                                   String description, String response, String status,
                                   Instant createdAt, List<SupportMessageDto> messages) { }

    public record DemoPaymentInput(@NotNull Long bookingId,
                                   @NotBlank @Pattern(regexp = "DEMO_CASH|DEMO_CARD|DEMO_UPI") String method) { }

    public record PaymentDto(Long id, String reference, String bookingReference, BigDecimal amount,
                             String method, String status, boolean demo, Instant createdAt) { }

    public record ActiveInput(boolean active) { }

    public record AdminOverview(Map<String, Long> counts, BigDecimal demoPaymentsTotal,
                                List<BookingDto> recentBookings, Map<String, Long> vehicleCategoryCounts,
                                Map<String, Long> bookingsByFacility, Map<String, Long> peakArrivalHours,
                                LocalDate from, LocalDate to) { }
}