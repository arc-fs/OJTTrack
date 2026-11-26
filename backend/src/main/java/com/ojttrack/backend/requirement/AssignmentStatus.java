package com.ojttrack.backend.requirement;

/** NOT_STARTED -> SUBMITTED -> CONFIRMED or RETURNED; RETURNED -> SUBMITTED (resubmit); CONFIRMED -> RETURNED (un-confirm). */
public enum AssignmentStatus {
    NOT_STARTED, SUBMITTED, CONFIRMED, RETURNED
}
