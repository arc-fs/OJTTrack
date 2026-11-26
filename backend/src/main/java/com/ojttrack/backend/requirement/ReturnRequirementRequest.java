package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST .../requirement-assignments/{id}/return. Feedback must contain real text. */
public record ReturnRequirementRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String feedback) {      // requirement_assignments.feedback VARCHAR(500)
}
