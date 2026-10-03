package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.model.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ApiMapper {
    private ApiMapper() { }

    public static UserDto user(AppUser user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole().name(), user.isActive(), user.getCreatedAt());
    }

    public static VehicleDto vehicle(Vehicle vehicle) {
        return new VehicleDto(vehicle.getId(), vehicle.getRegistration(), vehicle.getType(),
                vehicle.getBrand(), vehicle.getModel(), vehicle.getColor());
    }

    public static LocationDto location(ParkingLocation location, List<ParkingSlot> slots) {
        int total = (int) slots.stream().filter(slot -> slot.getStatus() != SlotStatus.DISABLED).count();
        int available = (int) slots.stream().filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE).count();
        int occupied = (int) slots.stream().filter(slot -> slot.getStatus() == SlotStatus.OCCUPIED).count();
        return new LocationDto(location.getId(), location.getName(), location.getAddress(), location.getArea(),
                location.getCategory(), location.getDescription(), location.getOperatingHours(),
                Set.copyOf(location.getSupportedVehicleTypes()), location.getHourlyRate(), total,
                available, occupied, location.isActive(), location.isDemoData(), location.getMapUrl());
    }

    public static SlotDto slot(ParkingSlot slot) {
        return new SlotDto(slot.getId(), slot.getLocation().getId(), slot.getCode(), slot.getStatus(),
                Set.copyOf(slot.getVehicleTypes()), slot.getHourlyRate());
    }

    public static BookingDto booking(Booking booking) {
        return new BookingDto(booking.getId(), booking.getReference(), booking.getLocation().getId(),
                booking.getLocation().getName(), booking.getSlot().getId(), booking.getSlot().getCode(),
                booking.getVehicle().getRegistration(), booking.getOwner().getName(), booking.getStartAt(),
                booking.getEndAt(), booking.getEntryAt(), booking.getExitAt(), booking.getStatus(),
                booking.getEstimatedFee(), booking.getFinalFee(), booking.getPaymentStatus());
    }

    public static NotificationDto notification(NotificationRecord notification) {
        return new NotificationDto(notification.getId(), notification.getTitle(), notification.getMessage(),
                notification.getCreatedAt(), notification.getReadAt() != null);
    }

    public static PaymentDto payment(PaymentRecord payment) {
        return new PaymentDto(payment.getId(), payment.getReference(), payment.getBooking().getReference(),
                payment.getAmount(), payment.getMethod(), payment.getStatus(), payment.isDemo(),
                payment.getCreatedAt());
    }

    public static SupportTicketDto ticket(SupportTicket ticket, List<SupportMessage> messages) {
        List<SupportMessageDto> mapped = messages.stream().map(message -> new SupportMessageDto(
                message.getId(), message.getAuthor().getName(), message.getAuthorRole(),
                message.getMessage(), message.getCreatedAt())).collect(Collectors.toList());
        String latestAdminResponse = messages.stream()
                .filter(message -> Role.ADMIN.name().equals(message.getAuthorRole()))
                .reduce((first, second) -> second)
                .map(SupportMessage::getMessage).orElse(null);
        return new SupportTicketDto(ticket.getId(), ticket.getOwner().getName(), ticket.getCategory(),
                ticket.getSubject(), ticket.getDescription(), latestAdminResponse, ticket.getStatus(),
                ticket.getCreatedAt(), mapped);
    }
}