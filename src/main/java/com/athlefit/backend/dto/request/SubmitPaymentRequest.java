package com.athlefit.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Details of the customer's manual bank transfer, checked by the venue owner. */
public record SubmitPaymentRequest(
        @NotBlank @Size(max = 255) String payerName,
        @NotBlank @Size(max = 100) String payerBank,
        @Size(max = 255) String paymentReference
) {
}
