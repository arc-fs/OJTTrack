package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of PUT /api/time-logs/{id}. version is the version the student saw, so an edit
 * based on stale data is rejected (409) instead of overwriting newer changes.
 * There is no placementId: an edit never moves a log to another placement.
 */
public record UpdateTimeLogRequest(
        @NotNull Integer version,
        @NotNull LocalDate logDate,
        @NotNull LocalTime timeIn,
        @NotNull LocalTime timeOut,
        boolean endsNextDay,
        @NotNull @PositiveOrZero @Max(TimeLog.MAX_SHIFT_MINUTES) Integer breakMinutes,
        @NotBlank @Size(max = 2000) String accomplishments)      // time_logs.accomplishments VARCHAR(2000)
        implements TimeLogFields {
}
