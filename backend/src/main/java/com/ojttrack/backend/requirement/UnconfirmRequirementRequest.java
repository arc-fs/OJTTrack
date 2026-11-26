package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST .../requirement-assignments/{id}/unconfirm. The reason becomes the student's feedback. */
public record UnconfirmRequirementRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason) {
}
