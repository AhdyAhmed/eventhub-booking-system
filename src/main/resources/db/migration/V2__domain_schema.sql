-- V2__domain_schema.sql
--
-- Day 2: core domain tables and their relationships.
--   venues (1) --- (*) events (1) --- (*) seats
--   users  (1) --- (*) bookings (1) --- (*) booking_items (*) --- (1) seats
--
-- No @Version / optimistic-locking column on seats yet — that's added in
-- V3 on Day 6, alongside the booking-creation flow that needs it.

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    full_name  VARCHAR(150) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE venues (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    address    VARCHAR(255),
    capacity   INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    venue_id    BIGINT NOT NULL REFERENCES venues (id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    category    VARCHAR(50),
    event_date  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_venue_id ON events (venue_id);
-- Supports the Day 4 "upcoming events" / date-range filtering queries.
CREATE INDEX idx_events_event_date ON events (event_date);

CREATE TABLE seats (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events (id),
    seat_number VARCHAR(20) NOT NULL,
    section     VARCHAR(50),
    price       NUMERIC(10, 2) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_seats_event_seat_number UNIQUE (event_id, seat_number)
);

CREATE INDEX idx_seats_event_id ON seats (event_id);
CREATE INDEX idx_seats_status ON seats (status);

CREATE TABLE bookings (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);

CREATE TABLE booking_items (
    id                BIGSERIAL PRIMARY KEY,
    booking_id        BIGINT NOT NULL REFERENCES bookings (id),
    seat_id           BIGINT NOT NULL REFERENCES seats (id),
    price_at_booking  NUMERIC(10, 2) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_items_booking_id ON booking_items (booking_id);
CREATE INDEX idx_booking_items_seat_id ON booking_items (seat_id);
