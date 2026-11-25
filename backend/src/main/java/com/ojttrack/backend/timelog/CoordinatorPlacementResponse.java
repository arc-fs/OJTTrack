package com.ojttrack.backend.timelog;

import java.time.LocalDate;

/** An active placement of one of the coordinator's students, with its progress. */
public record CoordinatorPlacementResponse(
        Integer placementId,
        String studentName,
        String companyName,
        LocalDate startDate,
        LocalDate endDate,
        Integer requiredHours,
        TimeLogSummaryResponse progress) {
}
