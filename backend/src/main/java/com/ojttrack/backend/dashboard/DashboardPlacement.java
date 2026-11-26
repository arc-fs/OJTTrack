package com.ojttrack.backend.dashboard;

import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.timelog.TimeLog;
import com.ojttrack.backend.timelog.TimeLogSummaryResponse;
import java.time.LocalDate;
import java.util.List;

/** The placement a dashboard shows for a student, with its progress (server-calculated). */
public record DashboardPlacement(
        Integer placementId,
        String companyName,
        PlacementStatus status,
        LocalDate startDate,
        LocalDate endDate,
        TimeLogSummaryResponse progress) {

    static DashboardPlacement of(Placement placement, String companyName, List<TimeLog> logsOfPlacement) {
        return new DashboardPlacement(placement.getId(), companyName, placement.getStatus(),
                placement.getStartDate(), placement.getEndDate(),
                TimeLogSummaryResponse.of(placement, logsOfPlacement));
    }
}
