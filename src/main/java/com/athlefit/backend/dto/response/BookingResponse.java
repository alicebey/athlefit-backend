package com.athlefit.backend.dto.response;

import com.athlefit.backend.model.Booking;
import com.athlefit.backend.model.BookingActor;
import com.athlefit.backend.model.BookingStatus;
import com.athlefit.backend.model.UserAccount;
import com.athlefit.backend.model.Venue;
import com.athlefit.backend.model.VenueTime;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Booking as seen by its customer or by the venue owner. All timestamps use the venue time zone
 * (Asia/Jakarta, +07:00) so clients can display them without converting device time zones.
 */
public record BookingResponse(
        UUID id,
        int durationHours,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        BigDecimal hourlyRate,
        BigDecimal totalPrice,
        BookingStatus status,
        String courtName,
        VenueResponse venue,
        OffsetDateTime createdAt,
        OffsetDateTime paymentDeadline,
        PaymentAccount paymentAccount,
        OffsetDateTime paymentSubmittedAt,
        String payerName,
        String payerBank,
        String paymentReference,
        OffsetDateTime paymentConfirmedAt,
        OffsetDateTime cancelledAt,
        BookingActor cancelledBy,
        String cancellationReason,
        boolean cancellable,
        OffsetDateTime cancellableUntil,
        boolean refundRequired,
        Customer customer
) {
    public record PaymentAccount(String bankName, String accountNumber, String accountHolder) {
    }

    public record Customer(String fullName, String email, String phone) {
    }

    public static BookingResponse from(
            Booking booking,
            VenueResponse venue,
            boolean cancellable,
            OffsetDateTime cancellableUntil
    ) {
        Venue bookedVenue = booking.getVenue();
        PaymentAccount paymentAccount = bookedVenue.hasPaymentAccount()
                ? new PaymentAccount(
                        bookedVenue.getBankName(),
                        bookedVenue.getBankAccountNumber(),
                        bookedVenue.getBankAccountHolder()
                )
                : null;
        UserAccount user = booking.getUser();
        Customer customer = user == null
                ? null
                : new Customer(user.getFullName(), user.getEmail(), user.getPhone());
        return new BookingResponse(
                booking.getId(),
                booking.getDurationHours(),
                VenueTime.local(booking.getStartAt()),
                VenueTime.local(booking.getEndAt()),
                booking.getHourlyRateSnapshot(),
                booking.getTotalPrice(),
                booking.getStatus(),
                booking.getCourt().getName(),
                venue,
                VenueTime.local(booking.getCreatedAt()),
                VenueTime.local(booking.getPaymentDeadline()),
                paymentAccount,
                VenueTime.local(booking.getPaymentSubmittedAt()),
                booking.getPayerName(),
                booking.getPayerBank(),
                booking.getPaymentReference(),
                VenueTime.local(booking.getPaymentConfirmedAt()),
                VenueTime.local(booking.getCancelledAt()),
                booking.getCancelledBy(),
                booking.getCancellationReason(),
                cancellable,
                VenueTime.local(cancellableUntil),
                booking.isRefundRequired(),
                customer
        );
    }
}
