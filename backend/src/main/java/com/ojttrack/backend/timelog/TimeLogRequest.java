package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body of POST /api/time-logs (create) and PUT /api/time-logs/{id} (edit).
 * version is required for edits: it is the version the student saw, so an edit based
 * on stale data is rejected (409) instead of overwriting newer changes.
 * Cross-field shift rules (overnight flag, 16-hour limit, break < shift) are checked
 * in TimeLog.shiftProblem so they have one implementation.
 */
public record TimeLogRequest(
        @NotNull LocalDate logDate,
        @NotNull LocalTime timeIn,
        @NotNull LocalTime timeOut,
        boolean endsNextDay,
        @NotNull @PositiveOrZero @Max(TimeLog.MAX_SHIFT_MINUTES) Integer breakMinutes,
        @NotBlank @Size(max = 2000) String accomplishments,     // time_logs.accomplishments VARCHAR(2000)
        Integer version) {

    /** Whole minutes only (V5 chk_time_logs_whole_minutes): seconds would be silently dropped. */
    @AssertTrue(message = "Times must be whole minutes (no seconds)")
    public boolean isWholeMinutes() {
        return wholeMinute(timeIn) && wholeMinute(timeOut);
    }

    private static boolean wholeMinute(LocalTime time) {
        return time == null || (time.getSecond() == 0 && time.getNano() == 0);   // null is reported by @NotNull
    }
}
