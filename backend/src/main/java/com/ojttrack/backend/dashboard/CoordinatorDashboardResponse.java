package com.ojttrack.backend.dashboard;

import com.ojttrack.backend.requirement.RequirementSummary.OverdueItem;
import java.time.LocalDate;
import java.util.List;

/** GET /api/coordinator/dashboard: totals plus one row per student, flagged students first. */
public record CoordinatorDashboardResponse(Totals totals, List<StudentRow> students) {

    /**
     * Counts over the coordinator's own students. Placements are counted as placements,
     * not students. logsAwaitingReview = PENDING logs on ACTIVE placements, i.e. exactly
     * what the Review queue shows (logs of ended placements are frozen and not counted).
     * requirementsAwaitingReview = SUBMITTED assignments of non-archived requirements this
     * coordinator OWNS (any student), i.e. exactly what they can review in Requirements.
     */
    public record Totals(int students, long activePlacements, long completedPlacements, long logsAwaitingReview,
                         long requirementsAwaitingReview) {}

    /** overdueRequirements: this coordinator's own non-archived requirements, overdue for this student. */
    public record StudentRow(Integer studentId, String fullName, String email, LocalDate placementSearchStartsOn,
                             DashboardPlacement placement, List<AssistanceFlag> flags,
                             List<OverdueItem> overdueRequirements) {}
}
