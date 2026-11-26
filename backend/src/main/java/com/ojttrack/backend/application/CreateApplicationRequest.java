package com.ojttrack.backend.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Body of POST and PUT /api/applications. PUT replaces every field, so clients must
 * send the current followUpDate when changing only the status (null clears it).
 */
public record CreateApplicationRequest(
        @NotBlank @Size(max = 200) String companyName,   // matches VARCHAR(200) in V2
        @NotNull ApplicationStatus status,
        LocalDate followUpDate) {                         // optional; past = overdue
}
