package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/coordinator/time-logs/{id}/return. Feedback must contain real text. */
public record ReturnTimeLogRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String feedback) {      // time_logs.feedback VARCHAR(500)
}
