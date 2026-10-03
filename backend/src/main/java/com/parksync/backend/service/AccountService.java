package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.*;
import com.parksync.backend.repository.BookingRepository;
import com.parksync.backend.repository.UserRepository;
import com.parksync.backend.repository.VehicleRepository;
import com.parksync.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationService notificationService;

    public AccountService(UserRepository users, VehicleRepository vehicles, BookingRepository bookings,
                          PasswordEncoder passwordEncoder, JwtService jwtService,
                          NotificationService notificationService) {
        this.users = users;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.notificationService = notificationService;
    }

    @Transactional
    public AuthResponse register(RegistrationInput input) {
        String email = normalizeEmail(input.email());
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("That email is already registered.");
        AppUser user = new AppUser();
        user.setName(input.name().trim());
        user.setEmail(email);
        user.setPhone(input.phone().trim());
        user.setRole(Role.CUSTOMER);
        user.setPasswordHash(passwordEncoder.encode(input.password()));
        user = users.save(user);
        notificationService.create(user, "Welcome to PARKSYNC",
                "Your account is ready. Initial facility listings marked DEMO DATA are examples, not live operator inventory.");
        return new AuthResponse(jwtService.issue(user), ApiMapper.user(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginInput input) {
        AppUser user = users.findByEmailIgnoreCase(normalizeEmail(input.email()))
                .filter(candidate -> passwordEncoder.matches(input.password(), candidate.getPasswordHash()))
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect."));
        return new AuthResponse(jwtService.issue(user), ApiMapper.user(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return ApiMapper.user(findUser(userId));
    }

    @Transactional
    public UserDto updateProfile(Long userId, ProfileUpdate input) {
        AppUser user = findUser(userId);
        user.setName(input.name().trim());
        user.setPhone(input.phone().trim());
        return ApiMapper.user(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeInput input) {
        AppUser user = findUser(userId);
        if (!passwordEncoder.matches(input.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect.");
        }
        if (passwordEncoder.matches(input.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Choose a password different from your current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(input.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        notificationService.create(user, "Password changed",
                "Your PARKSYNC password was changed. Sign in again on other devices.");
    }

    @Transactional
    public void logout(Long userId) {
        AppUser user = findUser(userId);
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    @Transactional(readOnly = true)
    public List<VehicleDto> vehicles(Long ownerId) {
        findUser(ownerId);
        return vehicles.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(ApiMapper::vehicle).toList();
    }

    @Transactional
    public VehicleDto createVehicle(Long ownerId, VehicleInput input) {
        AppUser owner = findUser(ownerId);
        String registration = normalizeRegistration(input.registration());
        if (vehicles.existsByRegistrationIgnoreCase(registration)) {
            throw ApiException.conflict("A vehicle with that registration is already registered.");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setRegistration(registration);
        vehicle.setType(input.type());
        vehicle.setBrand(input.brand().trim());
        vehicle.setModel(input.model().trim());
        vehicle.setColor(clean(input.color()));
        return ApiMapper.vehicle(vehicles.save(vehicle));
    }

    @Transactional
    public VehicleDto updateVehicle(Long ownerId, Long vehicleId, VehicleInput input) {
        Vehicle vehicle = findVehicle(ownerId, vehicleId);
        String registration = normalizeRegistration(input.registration());
        if (vehicles.existsByRegistrationIgnoreCaseAndIdNot(registration, vehicleId)) {
            throw ApiException.conflict("A vehicle with that registration is already registered.");
        }
        vehicle.setRegistration(registration);
        vehicle.setType(input.type());
        vehicle.setBrand(input.brand().trim());
        vehicle.setModel(input.model().trim());
        vehicle.setColor(clean(input.color()));
        return ApiMapper.vehicle(vehicle);
    }

    @Transactional
    public void deleteVehicle(Long ownerId, Long vehicleId) {
        Vehicle vehicle = findVehicle(ownerId, vehicleId);
        if (bookings.existsByVehicleId(vehicleId)) {
            throw ApiException.conflict("This vehicle has booking history, so it cannot be deleted.");
        }
        vehicles.delete(vehicle);
    }

    @Transactional(readOnly = true)
    public CustomerDashboard dashboard(Long ownerId) {
        findUser(ownerId);
        List<VehicleDto> ownedVehicles = vehicles.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream().map(ApiMapper::vehicle).toList();
        List<Booking> ownedBookings = bookings.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        List<BookingDto> recent = ownedBookings.stream().limit(10).map(ApiMapper::booking).toList();
        List<NotificationDto> recentNotifications = notificationService.list(ownerId).stream().limit(10).toList();
        int activeCount = (int) ownedBookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.UPCOMING
                        || booking.getStatus() == BookingStatus.ACTIVE).count();
        return new CustomerDashboard(ownedVehicles, recent, recentNotifications, activeCount);
    }

    @Transactional(readOnly = true)
    public AppUser findUser(Long userId) {
        return users.findById(userId).filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
    }

    @Transactional(readOnly = true)
    public Vehicle findVehicle(Long ownerId, Long vehicleId) {
        return vehicles.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
    }

    private static String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeRegistration(String value) {
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}