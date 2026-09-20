package com.athlefit.backend.model;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
    PENDING_PAYMENT,
    WAITING_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    /** Statuses that hold a court; mirrors the PostgreSQL exclusion constraint. */
    public static final Set<BookingStatus> ACTIVE =
            EnumSet.of(PENDING_PAYMENT, WAITING_CONFIRMATION, CONFIRMED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
