ALTER TABLE venues
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL', 'GOOGLE')),
    ADD COLUMN google_place_id VARCHAR(255) UNIQUE,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE venues
    ALTER COLUMN name DROP NOT NULL,
    ALTER COLUMN address DROP NOT NULL,
    ALTER COLUMN latitude DROP NOT NULL,
    ALTER COLUMN longitude DROP NOT NULL,
    ALTER COLUMN open_time DROP NOT NULL,
    ALTER COLUMN close_time DROP NOT NULL;

CREATE TABLE venue_sports (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    sport_id UUID NOT NULL REFERENCES sports(id),
    hourly_rate NUMERIC(12, 2) NOT NULL CHECK (hourly_rate > 0),
    UNIQUE (venue_id, sport_id)
);

INSERT INTO venue_sports (id, venue_id, sport_id, hourly_rate)
SELECT md5('venue-sport:' || id::text)::uuid, id, sport_id, hourly_rate
FROM venues;

CREATE TABLE venue_courts (
    id UUID PRIMARY KEY,
    venue_sport_id UUID NOT NULL REFERENCES venue_sports(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (venue_sport_id, name)
);

INSERT INTO venue_courts (id, venue_sport_id, name)
SELECT md5('court:' || id::text)::uuid, id, 'Court 1'
FROM venue_sports;

ALTER TABLE bookings
    ADD COLUMN court_id UUID;

UPDATE bookings booking
SET court_id = court.id
FROM venue_sports venue_sport
JOIN venue_courts court ON court.venue_sport_id = venue_sport.id
WHERE venue_sport.venue_id = booking.venue_id;

ALTER TABLE bookings
    ALTER COLUMN court_id SET NOT NULL,
    ADD CONSTRAINT bookings_court_id_fkey
        FOREIGN KEY (court_id) REFERENCES venue_courts(id);

ALTER TABLE bookings
    DROP CONSTRAINT no_overlapping_confirmed_bookings;

ALTER TABLE bookings
    DROP COLUMN venue_id;

ALTER TABLE venues
    DROP COLUMN sport_id,
    DROP COLUMN hourly_rate;

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_confirmed_bookings
    EXCLUDE USING gist (
        court_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status = 'CONFIRMED');

CREATE INDEX idx_venue_sports_venue_id ON venue_sports(venue_id);
CREATE INDEX idx_venue_sports_sport_id ON venue_sports(sport_id);
CREATE INDEX idx_venue_courts_venue_sport_id ON venue_courts(venue_sport_id);
CREATE INDEX idx_bookings_court_id ON bookings(court_id);
