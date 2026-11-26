package com.ojttrack.backend.dashboard;

import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.requirement.AssignmentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** GET /api/dashboard: everything the student's Dashboard tab shows. */
public record StudentDashboardResponse(
        Map<ApplicationStatus, Long> applicationCounts,
        List<FollowUp> followUps,
        DashboardPlacement placement,                 // ACTIVE, else most recently COMPLETED, else null
        PendingRequest pendingPlacementRequest,       // null when there is none
        Requirements requirements) {                  // non-archived requirements needing attention

    /** An application to follow up on: overdue, or due within 7 days. */
    public record FollowUp(Integer applicationId, String companyName, ApplicationStatus status,
                           LocalDate followUpDate, boolean overdue) {}

    /** type is ACTIVATION or CHANGE, as in PlacementRequestResponse. */
    public record PendingRequest(String companyName, String type) {}

    /** Requirements the student should act on. Submitted ones awaiting review are never listed as overdue. */
    public record Requirements(List<RequirementItem> overdue, List<RequirementItem> dueSoon,
                               List<RequirementItem> returned) {}

    public record RequirementItem(Integer assignmentId, String title, LocalDate dueDate,
                                  AssignmentStatus status, String feedback) {}
}
