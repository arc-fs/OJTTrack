package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of POST /api/time-logs. placementId is the placement the student opened the
 * form for: if it is no longer their ACTIVE placement (a transfer was approved
 * meanwhile), the log is rejected with 409 instead of landing at another company.
 * Cross-field shift rules are checked in TimeLog.shiftProblem.
 */
public record CreateTimeLogRequest(
        @NotNull Integer placementId,
        @NotNull LocalDate logDate,
        @NotNull LocalTime timeIn,
        @NotNull LocalTime timeOut,
        boolean endsNextDay,
        @NotNull @PositiveOrZero @Max(TimeLog.MAX_SHIFT_MINUTES) Integer breakMinutes,
        @NotBlank @Size(max = 2000) String accomplishments)      // time_logs.accomplishments VARCHAR(2000)
        implements TimeLogFields {
}
