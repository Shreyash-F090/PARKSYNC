package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.AdminOverview;
import com.parksync.backend.dto.ApiDtos.BookingDto;
import com.parksync.backend.dto.ApiDtos.UserDto;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.*;
import com.parksync.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:00");

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final LocationRepository locations;
    private final ParkingSlotRepository slots;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final AuditLogService audit;

    public AdminService(UserRepository users, VehicleRepository vehicles, LocationRepository locations,
                        ParkingSlotRepository slots, BookingRepository bookings, PaymentRepository payments,
                        AuditLogService audit) {
        this.users = users;
        this.vehicles = vehicles;
        this.locations = locations;
        this.slots = slots;
        this.bookings = bookings;
        this.payments = payments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserDto> listUsers(String query) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return users.search(normalized).stream().map(ApiMapper::user).toList();
    }

    @Transactional
    public UserDto setUserActive(Long actorId, Long userId, boolean active) {
        AppUser actor = findUser(actorId);
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.conflict("Administrator accounts cannot be changed from customer management.");
        }
        user.setActive(active);
        user.setTokenVersion(user.getTokenVersion() + 1);
        audit.record(actor, active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                "USER", userId, "Account active set to " + active);
        return ApiMapper.user(user);
    }

    @Transactional(readOnly = true)
    public AdminOverview overview() {
        return summarize(null, null);
    }

    @Transactional(readOnly = true)
    public AdminOverview reports(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("The report start date must be on or before its end date.");
        }
        return summarize(from, to);
    }

    private AdminOverview summarize(LocalDate from, LocalDate to) {
        Instant start = from == null ? Instant.MIN : from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant endExclusive = to == null ? Instant.MAX : to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        List<Booking> matched = bookings.findAll().stream()
                .filter(booking -> !booking.getCreatedAt().isBefore(start) && booking.getCreatedAt().isBefore(endExclusive))
                .sorted(Comparator.comparing(Booking::getCreatedAt).reversed())
                .toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("bookings", (long) matched.size());
        counts.put("customers", users.count());
        counts.put("vehicles", vehicles.count());
        counts.put("facilities", locations.count());
        counts.put("parkingSlots", slots.count());
        counts.put("availableSlots", slots.countByStatus(SlotStatus.AVAILABLE));
        counts.put("occupiedSlots", slots.countByStatus(SlotStatus.OCCUPIED));
        counts.put("activeBookings", count(matched, BookingStatus.ACTIVE));
        counts.put("upcomingBookings", count(matched, BookingStatus.UPCOMING));
        counts.put("completedBookings", count(matched, BookingStatus.COMPLETED));
        counts.put("cancelledBookings", count(matched, BookingStatus.CANCELLED));

        List<PaymentRecord> matchedPayments = payments.findAllByOrderByCreatedAtDesc().stream()
                .filter(PaymentRecord::isDemo)
                .filter(payment -> !payment.getCreatedAt().isBefore(start)
                        && payment.getCreatedAt().isBefore(endExclusive))
                .toList();
        BigDecimal demoTotal = matchedPayments.stream().map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> vehicleCategories = groupAndSort(matched,
                booking -> booking.getVehicle().getType().name());
        Map<String, Long> facilities = groupAndSort(matched,
                booking -> booking.getLocation().getName());
        Map<String, Long> peakHours = groupAndSort(matched, booking -> HOUR_FORMAT.format(
                booking.getStartAt().atZone(REPORT_ZONE).toLocalTime()));

        List<BookingDto> recent = matched.stream().limit(10).map(ApiMapper::booking).toList();
        return new AdminOverview(counts, demoTotal, recent, vehicleCategories, facilities,
                peakHours, from, to);
    }

    private static long count(List<Booking> records, BookingStatus status) {
        return records.stream().filter(booking -> booking.getStatus() == status).count();
    }

    private static Map<String, Long> groupAndSort(List<Booking> records, Function<Booking, String> key) {
        return records.stream().collect(Collectors.groupingBy(key, Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (first, second) -> first, LinkedHashMap::new));
    }

    private AppUser findUser(Long id) {
        return users.findById(id).filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.notFound("Administrator account not found."));
    }
}