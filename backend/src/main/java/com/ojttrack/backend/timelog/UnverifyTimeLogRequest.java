package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/coordinator/time-logs/{id}/unverify. The reason becomes the log's
 * feedback, so the student sees why it came back.
 */
public record UnverifyTimeLogRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 500) String reason) {        // stored in time_logs.feedback VARCHAR(500)
}
