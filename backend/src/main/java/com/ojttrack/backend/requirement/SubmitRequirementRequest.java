package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/requirements/{assignmentId}/submit. The note is optional. No file uploads (MVP §6). */
public record SubmitRequirementRequest(
        @NotNull Integer version,
        @Size(max = 1000) String note) {                   // requirement_assignments.note VARCHAR(1000)
}
