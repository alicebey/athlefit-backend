CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    favorite_sport VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sports (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE venues (
    id UUID PRIMARY KEY,
    sport_id UUID NOT NULL REFERENCES sports(id),
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    rating NUMERIC(2, 1) CHECK (rating BETWEEN 0 AND 5),
    image_url VARCHAR(1000),
    phone VARCHAR(50),
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    hourly_rate NUMERIC(12, 2) NOT NULL CHECK (hourly_rate >= 0),
    CONSTRAINT valid_venue_hours CHECK (close_time > open_time)
);

CREATE TABLE venue_open_days (
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    day_name VARCHAR(20) NOT NULL CHECK (
        day_name IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    PRIMARY KEY (venue_id, day_name)
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    venue_id UUID NOT NULL REFERENCES venues(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    duration_hours INTEGER NOT NULL CHECK (duration_hours BETWEEN 1 AND 3),
    hourly_rate_snapshot NUMERIC(12, 2) NOT NULL CHECK (hourly_rate_snapshot >= 0),
    total_price NUMERIC(12, 2) NOT NULL CHECK (total_price >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT valid_booking_time CHECK (end_at > start_at)
);

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_confirmed_bookings
    EXCLUDE USING gist (
        venue_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED');

CREATE INDEX idx_venues_sport_id ON venues(sport_id);
CREATE INDEX idx_bookings_user_start ON bookings(user_id, start_at DESC);
