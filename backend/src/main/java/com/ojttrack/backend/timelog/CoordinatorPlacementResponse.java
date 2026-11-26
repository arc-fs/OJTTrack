package com.ojttrack.backend.timelog;

import java.time.LocalDate;

/** An active placement of one of the coordinator's students, with its progress. */
public record CoordinatorPlacementResponse(
        Integer placementId,
        Integer studentId,      // lets the Progress tab load this student's requirement summary
        String studentName,
        String companyName,
        String role,            // null for placements created before roles were recorded (V8)
        LocalDate startDate,
        LocalDate endDate,
        Integer requiredHours,
        TimeLogSummaryResponse progress) {
}
