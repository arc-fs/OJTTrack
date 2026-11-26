package com.ojttrack.backend.dashboard;

import java.time.LocalDate;
import java.util.List;

/** GET /api/coordinator/dashboard: totals plus one row per student, flagged students first. */
public record CoordinatorDashboardResponse(Totals totals, List<StudentRow> students) {

    /**
     * Counts over the coordinator's own students. Placements are counted as placements,
     * not students. logsAwaitingReview = PENDING logs on ACTIVE placements, i.e. exactly
     * what the Review queue shows (logs of ended placements are frozen and not counted).
     */
    public record Totals(int students, long activePlacements, long completedPlacements, long logsAwaitingReview) {}

    public record StudentRow(Integer studentId, String fullName, String email, LocalDate placementSearchStartsOn,
                             DashboardPlacement placement, List<AssistanceFlag> flags) {}
}
