package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.*;
import com.parksync.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final LocationRepository locations;
    private final ParkingSlotRepository slots;
    private final PaymentRepository payments;
    private final NotificationService notifications;
    private final AuditLogService audit;
    private final SecureRandom random = new SecureRandom();

    public BookingService(BookingRepository bookings, UserRepository users, VehicleRepository vehicles,
                         LocationRepository locations, ParkingSlotRepository slots, PaymentRepository payments,
                         NotificationService notifications, AuditLogService audit) {
        this.bookings = bookings;
        this.users = users;
        this.vehicles = vehicles;
        this.locations = locations;
        this.slots = slots;
        this.payments = payments;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BookingQuote quote(Long ownerId, BookingQuoteInput input) {
        AppUser owner = activeUser(ownerId);
        Vehicle vehicle = vehicles.findByIdAndOwnerId(input.vehicleId(), owner.getId())
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        ParkingLocation location = locations.findByIdAndActiveTrue(input.locationId())
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        validatePeriod(input.startAt(), input.endAt());
        if (!location.getSupportedVehicleTypes().contains(vehicle.getType())) {
            throw ApiException.conflict("This facility does not list support for the selected vehicle type.");
        }
        List<ParkingSlot> eligible = slots.findAllByLocationIdOrderByCodeAsc(location.getId()).stream()
                .filter(slot -> slot.getStatus() != SlotStatus.DISABLED)
                .filter(slot -> slot.getVehicleTypes().contains(vehicle.getType()))
                .filter(slot -> !bookings.existsOverlap(slot.getId(), BookingStatus.CANCELLED,
                        input.startAt(), input.endAt()))
                .toList();
        int billableHours = FeeCalculator.billableHours(input.startAt(), input.endAt());
        BigDecimal rate = eligible.isEmpty() ? location.getHourlyRate()
                : eligible.getFirst().getHourlyRate() != null
                    ? eligible.getFirst().getHourlyRate() : location.getHourlyRate();
        String rule = "Hourly rate × " + billableHours
                + (billableHours == 1 ? " billable hour" : " billable hours"); 
        return new BookingQuote(FeeCalculator.estimate(rate, input.startAt(), input.endAt()),
                billableHours, "INR", eligible.stream().map(ApiMapper::slot).toList(), rule);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> myBookings(Long ownerId, String status) {
        List<Booking> result = bookings.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        if (status == null || status.isBlank()) return result.stream().map(ApiMapper::booking).toList();
        BookingStatus requested = parseStatus(status);
        return result.stream().filter(booking -> booking.getStatus() == requested)
                .map(ApiMapper::booking).toList();
    }

    @Transactional(readOnly = true)
    public BookingDto myBooking(Long ownerId, Long id) {
        return bookings.findByIdAndOwnerId(id, ownerId).map(ApiMapper::booking)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
    }

    @Transactional
    public BookingDto create(Long ownerId, BookingInput input) {
        AppUser owner = activeUser(ownerId);
        Vehicle vehicle = vehicles.findByIdAndOwnerId(input.vehicleId(), ownerId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        ParkingLocation location = locations.findByIdAndActiveTrue(input.locationId())
                .orElseThrow(() -> ApiException.notFound("Parking facility not found."));
        validatePeriod(input.startAt(), input.endAt());
        if (!location.getSupportedVehicleTypes().contains(vehicle.getType())) {
            throw ApiException.conflict("This facility does not list support for the selected vehicle type.");
        }

        ParkingSlot slot = slots.findByIdForUpdate(input.slotId())
                .orElseThrow(() -> ApiException.notFound("Parking slot not found."));
        if (!slot.getLocation().getId().equals(location.getId())) {
            throw ApiException.badRequest("The selected slot does not belong to this facility.");
        }
        if (slot.getStatus() == SlotStatus.DISABLED || !slot.getVehicleTypes().contains(vehicle.getType())
                || overlaps(slot.getId(), input.startAt(), input.endAt())) {
            throw ApiException.conflict("That slot is no longer available for the selected visit time.");
        }
        BigDecimal rate = slot.getHourlyRate() == null ? location.getHourlyRate() : slot.getHourlyRate();
        Booking booking = new Booking();
        booking.setReference(reference("PS"));
        booking.setOwner(owner);
        booking.setVehicle(vehicle);
        booking.setLocation(location);
        booking.setSlot(slot);
        booking.setStartAt(input.startAt());
        booking.setEndAt(input.endAt());
        booking.setStatus(BookingStatus.UPCOMING);
        booking.setEstimatedFee(FeeCalculator.estimate(rate, input.startAt(), input.endAt()));
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking = bookings.save(booking);
        notifications.create(owner, "Parking reserved",
                "Reservation " + booking.getReference() + " is confirmed for " + location.getName()
                        + ". Check facility details before arrival; demo facilities are sample listings.");
        audit.record(owner, "BOOKING_CREATED", "BOOKING", booking.getId(), booking.getReference());
        return ApiMapper.booking(booking);
    }

    @Transactional
    public BookingDto cancel(Long ownerId, Long id) {
        Booking booking = bookings.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getStatus() != BookingStatus.UPCOMING || !booking.getStartAt().isAfter(Instant.now())) {
            throw ApiException.conflict("Only upcoming bookings can be cancelled before their arrival time.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        notifications.create(booking.getOwner(), "Reservation cancelled",
                "Reservation " + booking.getReference() + " was cancelled. Its history remains available.");
        audit.record(booking.getOwner(), "BOOKING_CANCELLED", "BOOKING", booking.getId(), booking.getReference());
        return ApiMapper.booking(booking);
    }

    @Transactional
    public BookingDto recordEntry(Long adminId, Long id) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getStatus() != BookingStatus.UPCOMING) {
            throw ApiException.conflict("Only an upcoming booking can be checked in.");
        }
        ParkingSlot slot = slots.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> ApiException.notFound("Parking slot not found."));
        if (slot.getStatus() == SlotStatus.DISABLED
                || bookings.existsBySlotIdAndStatusIn(slot.getId(), List.of(BookingStatus.ACTIVE))
                || !bookings.lockOverlappingExcept(slot.getId(), booking.getId(), BookingStatus.CANCELLED,
                        booking.getStartAt(), booking.getEndAt()).isEmpty()) {
            throw ApiException.conflict("This slot is not accepting check-ins.");
        }
        booking.setStatus(BookingStatus.ACTIVE);
        booking.setEntryAt(Instant.now());
        slot.setStatus(SlotStatus.OCCUPIED);
        notifications.create(booking.getOwner(), "Vehicle entry recorded",
                "Entry was recorded for booking " + booking.getReference() + ".");
        audit.record(activeUser(adminId), "BOOKING_ENTRY", "BOOKING", id, booking.getReference());
        return ApiMapper.booking(booking);
    }

    @Transactional
    public BookingDto recordExit(Long adminId, Long id) {
        Booking booking = bookings.findById(id)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getStatus() != BookingStatus.ACTIVE || booking.getEntryAt() == null) {
            throw ApiException.conflict("Only an active booking with a recorded entry can be checked out.");
        }
        Instant exit = Instant.now();
        if (!exit.isAfter(booking.getEntryAt())) exit = booking.getEntryAt().plusSeconds(1);
        BigDecimal rate = booking.getSlot().getHourlyRate() == null
                ? booking.getLocation().getHourlyRate() : booking.getSlot().getHourlyRate();
        booking.setExitAt(exit);
        booking.setFinalFee(FeeCalculator.estimate(rate, booking.getEntryAt(), exit));
        booking.setStatus(BookingStatus.COMPLETED);
        ParkingSlot slot = slots.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> ApiException.notFound("Parking slot not found."));
        Instant now = Instant.now();
        boolean reservedNow = !bookings.lockOverlappingExcept(slot.getId(), booking.getId(), BookingStatus.CANCELLED,
                now, now.plusSeconds(1)).isEmpty();
        slot.setStatus(reservedNow ? SlotStatus.OCCUPIED : SlotStatus.AVAILABLE);
        notifications.create(booking.getOwner(), "Vehicle exit recorded",
                "Booking " + booking.getReference() + " is complete. Final fee: ₹" + booking.getFinalFee()
                        + ". Payment records in this app are demonstrations only.");
        audit.record(activeUser(adminId), "BOOKING_EXIT", "BOOKING", id,
                booking.getReference() + " final fee " + booking.getFinalFee());
        return ApiMapper.booking(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> adminBookings(String query, String status, Long locationId) {
        BookingStatus parsed = status == null || status.isBlank() ? null : parseStatus(status);
        return bookings.searchAdmin(clean(query), parsed, locationId).stream().map(ApiMapper::booking).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> recentBookings(int limit) {
        return bookings.findTop10ByOrderByCreatedAtDesc().stream().limit(limit)
                .map(ApiMapper::booking).toList();
    }

    @Transactional
    public PaymentDto demoPayment(Long ownerId, DemoPaymentInput input) {
        AppUser owner = activeUser(ownerId);
        Booking booking = bookings.findByIdAndOwnerId(input.bookingId(), ownerId)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.conflict("A cancelled booking cannot be paid.");
        }
        if (booking.getPaymentStatus() != PaymentStatus.UNPAID || payments.existsByBookingId(booking.getId())) {
            throw ApiException.conflict("A payment record already exists for this booking.");
        }
        BigDecimal amount = booking.getFinalFee() == null ? booking.getEstimatedFee() : booking.getFinalFee();
        PaymentRecord payment = new PaymentRecord();
        payment.setReference(reference("DEMO"));
        payment.setBooking(booking);
        payment.setOwner(owner);
        payment.setAmount(amount);
        payment.setMethod(input.method().toUpperCase(Locale.ROOT));
        payment.setStatus("DEMO_PAID");
        payment.setDemo(true);
        payment = payments.save(payment);
        booking.setPaymentStatus(PaymentStatus.DEMO_PAID);
        notifications.create(owner, "Demo payment recorded",
                "A simulated payment record was created for booking " + booking.getReference()
                        + ". No money was charged.");
        audit.record(owner, "DEMO_PAYMENT_RECORDED", "PAYMENT", payment.getId(),
                "Simulated payment only; amount=" + amount);
        return ApiMapper.payment(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> myPayments(Long ownerId) {
        return payments.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(ApiMapper::payment).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> allPayments(String status) {
        List<PaymentRecord> result = status == null || status.isBlank()
                ? payments.findAllByOrderByCreatedAtDesc()
                : payments.findAllByStatusOrderByCreatedAtDesc(status.toUpperCase(Locale.ROOT));
        return result.stream().map(ApiMapper::payment).toList();
    }

    private boolean overlaps(Long slotId, Instant start, Instant end) {
        return !bookings.lockOverlapping(slotId, BookingStatus.CANCELLED, start, end).isEmpty();
    }

    private AppUser activeUser(Long id) {
        return users.findById(id).filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
    }

    private static void validatePeriod(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("Departure must be after arrival.");
        }
        if (start.isBefore(Instant.now().minusSeconds(60))) {
            throw ApiException.badRequest("Arrival time must be in the future.");
        }
        try {
            FeeCalculator.billableHours(start, end);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static BookingStatus parseStatus(String status) {
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown booking status.");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String reference(String prefix) {
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        StringBuilder value = new StringBuilder(prefix).append('-');
        for (byte item : bytes) value.append(String.format("%02X", item));
        return value.toString();
    }
}