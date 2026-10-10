package com.parksync.backend.service;

import com.parksync.backend.dto.ApiDtos.BookingDto;
import com.parksync.backend.dto.ApiDtos.BookingInput;
import com.parksync.backend.dto.ApiDtos.BookingQuoteInput;
import com.parksync.backend.dto.ApiDtos.RegistrationInput;
import com.parksync.backend.exception.ApiException;
import com.parksync.backend.model.*;
import com.parksync.backend.repository.BookingRepository;
import com.parksync.backend.repository.LocationRepository;
import com.parksync.backend.repository.ParkingSlotRepository;
import com.parksync.backend.repository.UserRepository;
import com.parksync.backend.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:parksync-availability;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "parksync.jwt.secret=park-sync-test-key-with-more-than-32-characters",
        "parksync.admin.email=",
        "parksync.admin.password="
})
@ActiveProfiles("test")
class BookingAvailabilityTest {
    @Autowired private AccountService accounts;
    @Autowired private BookingService bookings;
    @Autowired private LocationService locations;
    @Autowired private UserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private LocationRepository locationRepository;
    @Autowired private ParkingSlotRepository slots;
    @Autowired private BookingRepository bookingRepository;

    @Test
    void availabilityFollowsVisitIntervalAndCancellationReleasesIt() {
        var auth = accounts.register(new RegistrationInput(
                "Availability Driver", "availability@example.test", "+91 90000 22222",
                "SecurePass123!", true));
        AppUser owner = users.findById(auth.user().id()).orElseThrow();
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setRegistration("MH01AV2604");
        vehicle.setType(VehicleType.CAR);
        vehicle.setBrand("Test");
        vehicle.setModel("Visit");
        vehicle = vehicles.save(vehicle);

        ParkingLocation location = new ParkingLocation();
        location.setName("Interval Lot");
        location.setAddress("1 Test Road");
        location.setArea("Test");
        location.setCategory("PUBLIC");
        location.setDescription("Availability test facility");
        location.setOperatingHours("00:00-23:59");
        location.setSupportedVehicleTypes(Set.of(VehicleType.CAR));
        location.setHourlyRate(new BigDecimal("50.00"));
        location.setActive(true);
        location = locationRepository.save(location);

        ParkingSlot reserved = slot(location, "A1", SlotStatus.AVAILABLE);
        ParkingSlot occupied = slot(location, "B1", SlotStatus.OCCUPIED);
        Long locationId = location.getId();
        Long vehicleId = vehicle.getId();
        Long ownerId = owner.getId();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Instant later = end.plus(2, ChronoUnit.HOURS);

        var created = bookings.create(ownerId, new BookingInput(
                locationId, vehicleId, reserved.getId(), start, end));
        var duringVisit = bookings.quote(ownerId, new BookingQuoteInput(
                locationId, vehicleId, start.plus(30, ChronoUnit.MINUTES), end));
        assertTrue(duringVisit.slots().stream().noneMatch(slot -> slot.id().equals(reserved.getId())));
        assertTrue(duringVisit.slots().stream().anyMatch(slot -> slot.id().equals(occupied.getId())));

        assertThrows(ApiException.class, () -> bookings.create(ownerId, new BookingInput(
                locationId, vehicleId, reserved.getId(), start.plus(30, ChronoUnit.MINUTES), end)));
        var otherInterval = bookings.create(ownerId, new BookingInput(
                locationId, vehicleId, occupied.getId(), later, later.plus(1, ChronoUnit.HOURS)));
        assertEquals(occupied.getId(), otherInterval.slotId());

        bookings.cancel(ownerId, created.id());
        var afterCancel = bookings.quote(ownerId, new BookingQuoteInput(
                locationId, vehicleId, start, end));
        assertTrue(afterCancel.slots().stream().anyMatch(slot -> slot.id().equals(reserved.getId())));

        Booking current = new Booking();
        current.setReference("PS-NOW000000001");
        current.setOwner(owner);
        current.setVehicle(vehicle);
        current.setLocation(location);
        current.setSlot(reserved);
        current.setStartAt(Instant.now().minus(1, ChronoUnit.HOURS));
        current.setEndAt(Instant.now().plus(1, ChronoUnit.HOURS));
        current.setStatus(BookingStatus.UPCOMING);
        current.setEstimatedFee(new BigDecimal("50.00"));
        current.setPaymentStatus(PaymentStatus.UNPAID);
        bookingRepository.save(current);
        var listed = locations.getActive(locationId);
        assertEquals(2, listed.totalSlots());
        assertEquals(0, listed.availableSlots());
        assertEquals(1, listed.occupiedSlots());

        var checkedIn = bookings.recordEntry(ownerId, otherInterval.id());
        assertEquals(BookingStatus.ACTIVE, checkedIn.status());
        assertEquals(SlotStatus.OCCUPIED, slots.findById(occupied.getId()).orElseThrow().getStatus());
        var checkedOut = bookings.recordExit(ownerId, otherInterval.id());
        assertEquals(BookingStatus.COMPLETED, checkedOut.status());
        assertEquals(SlotStatus.AVAILABLE, slots.findById(occupied.getId()).orElseThrow().getStatus());
    }

    @Test
    void quoteBeforeSelectionFiltersVehicleType() {
        var auth = accounts.register(new RegistrationInput(
                "Quote Driver", "quote-before@example.test", "+91 90000 33333",
                "SecurePass123!", true));
        AppUser owner = users.findById(auth.user().id()).orElseThrow();
        Vehicle car = vehicle(owner, "MH01QT0001", VehicleType.CAR);
        Vehicle scooter = vehicle(owner, "MH01QT0002", VehicleType.SCOOTER);
        ParkingLocation location = facility("Quote Lot", Set.of(VehicleType.CAR, VehicleType.MOTORCYCLE));
        ParkingSlot carSlot = slot(location, "C1", SlotStatus.AVAILABLE, VehicleType.CAR);
        ParkingSlot bikeSlot = slot(location, "M1", SlotStatus.AVAILABLE, VehicleType.MOTORCYCLE);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var quote = bookings.quote(owner.getId(), new BookingQuoteInput(location.getId(), car.getId(), start, end));
        assertTrue(quote.slots().stream().anyMatch(item -> item.id().equals(carSlot.getId())));
        assertTrue(quote.slots().stream().noneMatch(item -> item.id().equals(bikeSlot.getId())));
        assertThrows(ApiException.class, () -> bookings.quote(owner.getId(),
                new BookingQuoteInput(location.getId(), scooter.getId(), start, end)));
        assertThrows(ApiException.class, () -> bookings.create(owner.getId(),
                new BookingInput(location.getId(), car.getId(), bikeSlot.getId(), start, end)));
    }

    @Test
    void oneOfTwoOverlappingSlotRequestsSucceeds() throws Exception {
        var auth = accounts.register(new RegistrationInput(
                "Race Driver", "race-booking@example.test", "+91 90000 44444",
                "SecurePass123!", true));
        AppUser owner = users.findById(auth.user().id()).orElseThrow();
        Vehicle car = vehicle(owner, "MH01RC0001", VehicleType.CAR);
        ParkingLocation location = facility("Race Lot", Set.of(VehicleType.CAR));
        ParkingSlot shared = slot(location, "R1", SlotStatus.AVAILABLE, VehicleType.CAR);
        Instant start = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> tasks = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            tasks.add(pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) return new IllegalStateException("Timed out waiting to start.");
                try {
                    return bookings.create(owner.getId(), new BookingInput(
                            location.getId(), car.getId(), shared.getId(), start, end));
                } catch (RuntimeException ex) {
                    return ex;
                }
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        List<Object> results = new ArrayList<>();
        for (Future<Object> task : tasks) results.add(task.get());
        long created = results.stream().filter(BookingDto.class::isInstance).count();
        long rejected = results.stream().filter(ApiException.class::isInstance).count();
        assertEquals(1, created);
        assertEquals(1, rejected);
        assertEquals(1, bookings.myBookings(owner.getId(), null).stream()
                .filter(booking -> shared.getId().equals(booking.slotId())).count());
    }

    private Vehicle vehicle(AppUser owner, String registration, VehicleType type) {
        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setRegistration(registration);
        vehicle.setType(type);
        vehicle.setBrand("Test");
        vehicle.setModel("Visit");
        return vehicles.save(vehicle);
    }

    private ParkingLocation facility(String name, Set<VehicleType> types) {
        ParkingLocation location = new ParkingLocation();
        location.setName(name);
        location.setAddress("1 Test Road");
        location.setArea("Test");
        location.setCategory("PUBLIC");
        location.setDescription("Availability test facility");
        location.setOperatingHours("00:00-23:59");
        location.setSupportedVehicleTypes(types);
        location.setHourlyRate(new BigDecimal("50.00"));
        location.setActive(true);
        return locationRepository.save(location);
    }

    private ParkingSlot slot(ParkingLocation location, String code, SlotStatus status, VehicleType type) {
        ParkingSlot slot = new ParkingSlot();
        slot.setLocation(location);
        slot.setCode(code);
        slot.setStatus(status);
        slot.setVehicleTypes(Set.of(type));
        return slots.save(slot);
    }

    private ParkingSlot slot(ParkingLocation location, String code, SlotStatus status) {
        ParkingSlot slot = new ParkingSlot();
        slot.setLocation(location);
        slot.setCode(code);
        slot.setStatus(status);
        slot.setVehicleTypes(Set.of(VehicleType.CAR));
        return slots.save(slot);
    }
}
