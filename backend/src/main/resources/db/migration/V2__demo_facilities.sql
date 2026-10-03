INSERT INTO parking_locations
    (id, name, address, area, category, description, operating_hours, hourly_rate, map_url, active, demo_data, created_at, updated_at)
VALUES
    (1, 'R City Mall', 'LBS Marg, Ghatkopar West, Mumbai, Maharashtra', 'Ghatkopar', 'MALL',
     'Demonstration listing for a multi-level mall parking facility. Availability is sample data, not connected to the mall operator.',
     '10:00–23:00', 60.00, 'https://maps.google.com/?q=R+City+Mall+Ghatkopar+Mumbai', TRUE, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, 'Phoenix Marketcity', 'LBS Marg, Kurla West, Mumbai, Maharashtra', 'Kurla', 'MALL',
     'Demonstration listing for a shopping destination. Availability is sample data, not connected to the mall operator.',
     '10:00–23:00', 80.00, 'https://maps.google.com/?q=Phoenix+Marketcity+Kurla+Mumbai', TRUE, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (3, 'Ordinary Public Parking — Mumbai', 'Station Road, Mumbai, Maharashtra', 'Mumbai', 'PUBLIC',
     'Example public parking listing for development and evaluation. Contact the local operator to confirm current conditions.',
     '06:00–23:00', 30.00, 'https://maps.google.com/?q=public+parking+Mumbai', TRUE, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO location_vehicle_types (location_id, vehicle_type)
VALUES
    (1, 'CAR'), (1, 'MOTORCYCLE'), (1, 'SCOOTER'), (1, 'OTHER'),
    (2, 'CAR'), (2, 'MOTORCYCLE'), (2, 'SCOOTER'), (2, 'OTHER'),
    (3, 'CAR'), (3, 'MOTORCYCLE'), (3, 'SCOOTER'), (3, 'OTHER');

INSERT INTO parking_slots (id, location_id, code, status, hourly_rate, created_at, updated_at)
VALUES
    (1, 1, 'RC-01', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, 1, 'RC-02', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (3, 1, 'RC-03', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (4, 1, 'RC-04', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (5, 1, 'RC-05', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (6, 1, 'RC-06', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (7, 1, 'RC-07', 'OCCUPIED', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (8, 1, 'RC-08', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (9, 2, 'PM-01', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (10, 2, 'PM-02', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (11, 2, 'PM-03', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (12, 2, 'PM-04', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (13, 2, 'PM-05', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (14, 2, 'PM-06', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (15, 2, 'PM-07', 'OCCUPIED', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (16, 2, 'PM-08', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (17, 3, 'PUB-01', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (18, 3, 'PUB-02', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (19, 3, 'PUB-03', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (20, 3, 'PUB-04', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (21, 3, 'PUB-05', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (22, 3, 'PUB-06', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (23, 3, 'PUB-07', 'OCCUPIED', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (24, 3, 'PUB-08', 'AVAILABLE', NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO slot_vehicle_types (slot_id, vehicle_type)
SELECT id, 'CAR' FROM parking_slots;

INSERT INTO slot_vehicle_types (slot_id, vehicle_type)
SELECT id, 'MOTORCYCLE' FROM parking_slots;

INSERT INTO slot_vehicle_types (slot_id, vehicle_type)
SELECT id, 'SCOOTER' FROM parking_slots;