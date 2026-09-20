package com.athlefit.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically releases courts held by bookings that were never paid. */
@Component
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingService bookingService;

    public BookingExpiryScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${athlefit.bookings.expiry-check-interval-ms:60000}")
    public void expireOverduePayments() {
        int expired = bookingService.expireOverduePayments();
        if (expired > 0) {
            log.info("Expired {} unpaid booking(s)", expired);
        }
    }
}
