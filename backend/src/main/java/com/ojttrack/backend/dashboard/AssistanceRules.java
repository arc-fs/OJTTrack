package com.ojttrack.backend.dashboard;

import com.ojttrack.backend.dashboard.AssistanceFlag.Code;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.timelog.CompletionEstimate;
import com.ojttrack.backend.timelog.TimeLog;
import com.ojttrack.backend.timelog.TimeLogStatus;
import com.ojttrack.backend.timelog.TimeLogSummaryResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * "Students who may need assistance" (MVP §4.6), as agreed with the developer.
 * Pure functions: no Spring, no database, no clock (today is passed in), so every rule
 * is tested with plain dates. Every applicable reason is returned, not just one.
 */
public final class AssistanceRules {

    /** No log of any status within this many calendar days (Manila) raises NO_RECENT_LOGS. */
    static final int RECENT_LOG_DAYS = 7;

    private AssistanceRules() {}

    /**
     * @param placementSearchStartsOn set by the coordinator; null = never "No placement yet"
     * @param placements all of the student's placements (any status)
     * @param logs logs of the student's placements; only the ACTIVE placement's logs are used
     * @param today today's date in Asia/Manila
     */
    public static List<AssistanceFlag> flagsFor(LocalDate placementSearchStartsOn, List<Placement> placements,
                                                List<TimeLog> logs, LocalDate today) {
        List<AssistanceFlag> flags = new ArrayList<>();
        Optional<Placement> active = placements.stream()
                .filter(p -> p.getStatus() == PlacementStatus.ACTIVE).findFirst();
        boolean completed = placements.stream().anyMatch(p -> p.getStatus() == PlacementStatus.COMPLETED);

        // NO_PLACEMENT: expected to be searching (date set and reached), but nothing active or completed.
        if (active.isEmpty() && !completed && placementSearchStartsOn != null
                && !today.isBefore(placementSearchStartsOn)) {
            flags.add(new AssistanceFlag(Code.NO_PLACEMENT,
                    "No placement yet (search started " + placementSearchStartsOn + ")"));
        }

        if (active.isEmpty()) {
            return flags;
        }
        Placement placement = active.get();
        // Old (terminated) placements never count: only this placement's logs from here on.
        List<TimeLog> activeLogs = logs.stream()
                .filter(l -> l.getPlacementId().equals(placement.getId())).toList();

        // NO_RECENT_LOGS: an attention signal, not proof of absence. Any status counts as activity;
        // measured by log_date (the work date). 7 calendar days always include a weekend.
        if (!placement.getStartDate().isAfter(today)) {
            Optional<LocalDate> lastLog = activeLogs.stream().map(TimeLog::getLogDate).max(Comparator.naturalOrder());
            LocalDate lastActivity = lastLog.orElse(placement.getStartDate());
            if (ChronoUnit.DAYS.between(lastActivity, today) > RECENT_LOG_DAYS) {
                flags.add(new AssistanceFlag(Code.NO_RECENT_LOGS, lastLog
                        .map(date -> "No logs since " + date)
                        .orElse("No logs yet since starting on " + placement.getStartDate())));
            }
        }

        // RETURNED_PENDING: corrections the student still has to make (ended placements are frozen).
        long returned = activeLogs.stream().filter(l -> l.getStatus() == TimeLogStatus.RETURNED).count();
        if (returned > 0) {
            flags.add(new AssistanceFlag(Code.RETURNED_PENDING,
                    returned + " returned log(s) waiting for the student's correction"));
        }

        // BEHIND_SCHEDULE: only with a reliable estimate (>= 5 verified logs over >= 7 days)
        // and a planned end date to compare with.
        TimeLogSummaryResponse progress = TimeLogSummaryResponse.of(placement, activeLogs);
        if (placement.getEndDate() != null
                && progress.estimateStatus() == CompletionEstimate.Status.AVAILABLE
                && progress.estimatedCompletionDate().isAfter(placement.getEndDate())) {
            flags.add(new AssistanceFlag(Code.BEHIND_SCHEDULE, "Estimated to finish "
                    + progress.estimatedCompletionDate() + ", after the planned end " + placement.getEndDate()));
        }
        return flags;
    }

    /** The placement a dashboard shows: ACTIVE, else the most recently completed, else none. */
    public static Optional<Placement> currentPlacement(List<Placement> placements) {
        return placements.stream().filter(p -> p.getStatus() == PlacementStatus.ACTIVE).findFirst()
                .or(() -> placements.stream()
                        .filter(p -> p.getStatus() == PlacementStatus.COMPLETED)
                        .max(Comparator.comparing(Placement::getEndedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))));
    }
}
