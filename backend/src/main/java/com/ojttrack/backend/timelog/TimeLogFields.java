package com.ojttrack.backend.timelog;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The fields a student enters for a log, shared by CreateTimeLogRequest and
 * UpdateTimeLogRequest (records implement these methods automatically). Lets the
 * service validate both the same way, and keeps the whole-minute rule in one place.
 */
public interface TimeLogFields {

    LocalDate logDate();
    LocalTime timeIn();
    LocalTime timeOut();
    boolean endsNextDay();
    Integer breakMinutes();
    String accomplishments();

    /** Whole minutes only (V5 chk_time_logs_whole_minutes): seconds would be silently dropped. */
    @AssertTrue(message = "Times must be whole minutes (no seconds)")
    default boolean isWholeMinutes() {
        return wholeMinute(timeIn()) && wholeMinute(timeOut());
    }

    private static boolean wholeMinute(LocalTime time) {
        return time == null || (time.getSecond() == 0 && time.getNano() == 0);   // null is reported by @NotNull
    }
}
