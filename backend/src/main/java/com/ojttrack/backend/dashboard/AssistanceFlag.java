package com.ojttrack.backend.dashboard;

/** One reason a coordinator may want to check on a student. A student can have several. */
public record AssistanceFlag(Code code, String message) {

    public enum Code { NO_PLACEMENT, NO_RECENT_LOGS, RETURNED_PENDING, BEHIND_SCHEDULE }
}
