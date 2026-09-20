-- Venue ownership and manual bank-transfer payment details.
ALTER TABLE venues
    ADD COLUMN owner_user_id UUID REFERENCES app_users(id),
    ADD COLUMN bank_name VARCHAR(100),
    ADD COLUMN bank_account_number VARCHAR(50),
    ADD COLUMN bank_account_holder VARCHAR(255);

CREATE INDEX idx_venues_owner_user_id ON venues(owner_user_id);

-- Demo venues get placeholder transfer details so the payment flow can be exercised locally.
UPDATE venues
SET bank_name = 'BCA (demo)',
    bank_account_number = '0000000000',
    bank_account_holder = name
WHERE source = 'MANUAL';

-- Booking lifecycle: PENDING_PAYMENT -> WAITING_CONFIRMATION -> CONFIRMED, or CANCELLED / EXPIRED.
ALTER TABLE bookings
    DROP CONSTRAINT no_overlapping_confirmed_bookings;

ALTER TABLE bookings
    DROP CONSTRAINT bookings_status_check;

ALTER TABLE bookings
    ADD COLUMN payment_deadline TIMESTAMPTZ,
    ADD COLUMN payment_submitted_at TIMESTAMPTZ,
    ADD COLUMN payer_name VARCHAR(255),
    ADD COLUMN payer_bank VARCHAR(100),
    ADD COLUMN payment_reference VARCHAR(255),
    ADD COLUMN payment_confirmed_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancelled_by VARCHAR(20),
    ADD COLUMN cancellation_reason VARCHAR(500);

-- Bookings created before payments existed were already treated as paid and confirmed.
UPDATE bookings
SET payment_confirmed_at = created_at
WHERE status = 'CONFIRMED';

UPDATE bookings
SET cancelled_at = created_at,
    cancelled_by = 'USER'
WHERE status = 'CANCELLED';

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check CHECK (
        status IN ('PENDING_PAYMENT', 'WAITING_CONFIRMATION', 'CONFIRMED', 'CANCELLED', 'EXPIRED')
    ),
    ADD CONSTRAINT bookings_cancelled_by_check CHECK (
        cancelled_by IS NULL OR cancelled_by IN ('USER', 'OWNER', 'SYSTEM')
    );

-- Every non-final booking holds its court, so two users can never pay for the same slot.
ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_active_bookings
    EXCLUDE USING gist (
        court_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING_PAYMENT', 'WAITING_CONFIRMATION', 'CONFIRMED'));

CREATE INDEX idx_bookings_pending_deadline
    ON bookings(payment_deadline)
    WHERE status = 'PENDING_PAYMENT';
