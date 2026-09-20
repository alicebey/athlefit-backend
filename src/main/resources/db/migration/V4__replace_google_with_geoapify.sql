ALTER TABLE venues
    DROP CONSTRAINT venues_source_check;

ALTER TABLE venues
    RENAME COLUMN google_place_id TO provider_place_id;

ALTER TABLE venues
    RENAME CONSTRAINT venues_google_place_id_key TO venues_provider_place_id_key;

UPDATE venues
SET source = 'GEOAPIFY',
    active = FALSE
WHERE source = 'GOOGLE';

ALTER TABLE venues
    ADD CONSTRAINT venues_source_check
        CHECK (source IN ('MANUAL', 'GEOAPIFY'));
