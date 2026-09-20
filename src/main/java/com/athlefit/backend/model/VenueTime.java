package com.athlefit.backend.model;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** All venues operate in Jakarta time; API timestamps are returned in this zone. */
public final class VenueTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    private VenueTime() {
    }

    public static OffsetDateTime local(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZONE).toOffsetDateTime();
    }
}
