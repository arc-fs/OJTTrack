package com.ojttrack.backend.placement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of POST /api/coordinator/placement-requests/{id}/approve. The coordinator sets the terms (decision B1). */
public record ApprovePlacementRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive @Max(5000) Integer requiredHours,
        @Size(max = 500) String note) {            // placement_requests.decision_note VARCHAR(500)
}
