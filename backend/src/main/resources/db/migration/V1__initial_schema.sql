CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    token_version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE vehicles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    registration VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    brand VARCHAR(80) NOT NULL,
    model VARCHAR(80) NOT NULL,
    color VARCHAR(50),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_registration UNIQUE (registration),
    CONSTRAINT fk_vehicle_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    INDEX ix_vehicle_owner (owner_id)
) ENGINE=InnoDB;

CREATE TABLE parking_locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(300) NOT NULL,
    area VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    operating_hours VARCHAR(100) NOT NULL,
    hourly_rate DECIMAL(10,2) NOT NULL,
    map_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    demo_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_location_active_name (active, name),
    INDEX ix_location_area (area)
) ENGINE=InnoDB;

CREATE TABLE location_vehicle_types (
    location_id BIGINT NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (location_id, vehicle_type),
    CONSTRAINT fk_location_vehicle_location FOREIGN KEY (location_id) REFERENCES parking_locations (id)
) ENGINE=InnoDB;

CREATE TABLE parking_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    location_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    hourly_rate DECIMAL(10,2),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_slot_location_code UNIQUE (location_id, code),
    CONSTRAINT fk_slot_location FOREIGN KEY (location_id) REFERENCES parking_locations (id),
    INDEX ix_slot_location_status (location_id, status)
) ENGINE=InnoDB;

CREATE TABLE slot_vehicle_types (
    slot_id BIGINT NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (slot_id, vehicle_type),
    CONSTRAINT fk_slot_vehicle_slot FOREIGN KEY (slot_id) REFERENCES parking_slots (id)
) ENGINE=InnoDB;

CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(24) NOT NULL,
    owner_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    entry_at DATETIME(3),
    exit_at DATETIME(3),
    status VARCHAR(20) NOT NULL,
    estimated_fee DECIMAL(10,2) NOT NULL,
    final_fee DECIMAL(10,2),
    payment_status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_reference UNIQUE (reference),
    CONSTRAINT fk_booking_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    CONSTRAINT fk_booking_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_booking_location FOREIGN KEY (location_id) REFERENCES parking_locations (id),
    CONSTRAINT fk_booking_slot FOREIGN KEY (slot_id) REFERENCES parking_slots (id),
    INDEX ix_booking_slot_period (slot_id, start_at, end_at, status),
    INDEX ix_booking_owner_created (owner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(24) NOT NULL,
    booking_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    demo_payment BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_reference UNIQUE (reference),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_payment_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    INDEX ix_payment_owner_created (owner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    read_at DATETIME(3),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    INDEX ix_notification_owner_created (owner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE support_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    description VARCHAR(3000) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_support_owner FOREIGN KEY (owner_id) REFERENCES app_users (id),
    INDEX ix_support_owner_created (owner_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE support_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    author_role VARCHAR(20) NOT NULL,
    message VARCHAR(3000) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_support_message_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id),
    CONSTRAINT fk_support_message_author FOREIGN KEY (author_id) REFERENCES app_users (id),
    INDEX ix_support_message_ticket (ticket_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT NOT NULL,
    details VARCHAR(1000),
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_users (id),
    INDEX ix_audit_entity_created (entity_type, entity_id, created_at)
) ENGINE=InnoDB;