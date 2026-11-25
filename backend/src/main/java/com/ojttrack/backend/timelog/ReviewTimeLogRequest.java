package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.NotNull;

/** Body of POST /api/coordinator/time-logs/{id}/verify: the version the coordinator reviewed. */
public record ReviewTimeLogRequest(@NotNull Integer version) {
}
