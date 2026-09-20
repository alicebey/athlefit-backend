package com.athlefit.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Email of a registered Athlefit user; blank removes the current owner. */
public record AssignOwnerRequest(@Email @Size(max = 255) String email) {
}
