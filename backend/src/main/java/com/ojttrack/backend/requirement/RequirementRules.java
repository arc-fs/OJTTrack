package com.ojttrack.backend.requirement;

import java.time.LocalDate;

/**
 * Due-date rules (agreed with the developer). Pure functions: today is passed in (from the
 * Asia/Manila Clock), so they are tested with plain dates. Never stored: a changed due date
 * takes effect everywhere immediately.
 */
public final class RequirementRules {

    /** "Due soon" = due today or within this many days. */
    public static final int DUE_SOON_DAYS = 7;

    private RequirementRules() {}

    /**
     * Overdue = a due date exists, it has passed (today is after it), the requirement is not
     * archived, and the student still has to act (NOT_STARTED or RETURNED). SUBMITTED is never
     * overdue (students aren't penalised for review delays); CONFIRMED is never overdue.
     */
    public static boolean isOverdue(AssignmentStatus status, LocalDate dueDate, boolean archived, LocalDate today) {
        return dueDate != null && !archived && studentMustAct(status) && today.isAfter(dueDate);
    }

    /** Due soon = the student still has to act and the due date is today or within 7 days. */
    public static boolean isDueSoon(AssignmentStatus status, LocalDate dueDate, boolean archived, LocalDate today) {
        return dueDate != null && !archived && studentMustAct(status)
                && !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(DUE_SOON_DAYS));
    }

    private static boolean studentMustAct(AssignmentStatus status) {
        return status == AssignmentStatus.NOT_STARTED || status == AssignmentStatus.RETURNED;
    }
}
