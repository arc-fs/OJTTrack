package com.ojttrack.backend.timelog;

import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Progress of ONE placement. Totals only ever include that placement's logs, so hours
 * from an earlier (terminated) placement can never count here. Only VERIFIED minutes
 * count toward the requirement. All amounts are whole minutes; the UI formats them.
 */
public record TimeLogSummaryResponse(
        Integer placementId,
        PlacementStatus placementStatus,
        long requiredMinutes,
        long verifiedMinutes,
        long pendingMinutes,
        int pendingCount,
        int returnedCount,
        long remainingMinutes,
        boolean requirementReached,
        CompletionEstimate.Status estimateStatus,
        LocalDate estimatedCompletionDate,
        LocalDate basedOnLogsUpTo) {

    static TimeLogSummaryResponse of(Placement placement, List<TimeLog> logs) {
        List<TimeLog> verified = logs.stream().filter(l -> l.getStatus() == TimeLogStatus.VERIFIED).toList();
        List<TimeLog> pending = logs.stream().filter(l -> l.getStatus() == TimeLogStatus.PENDING).toList();
        int returnedCount = (int) logs.stream().filter(l -> l.getStatus() == TimeLogStatus.RETURNED).count();

        long required = placement.getRequiredHours() * 60L;
        long verifiedMinutes = sumMinutes(verified);
        long remaining = Math.max(0, required - verifiedMinutes);

        CompletionEstimate estimate = CompletionEstimate.calculate(
                verified.stream()
                        .map(l -> new CompletionEstimate.VerifiedDay(l.getLogDate(), minutesOf(l)))
                        .toList(),
                remaining);

        return new TimeLogSummaryResponse(
                placement.getId(), placement.getStatus(), required, verifiedMinutes, sumMinutes(pending),
                pending.size(), returnedCount, remaining, verifiedMinutes >= required,
                estimate.status(), estimate.estimatedDate(), estimate.basedOnLogsUpTo());
    }

    private static long sumMinutes(List<TimeLog> logs) {
        return logs.stream().mapToLong(TimeLogSummaryResponse::minutesOf).sum();
    }

    private static int minutesOf(TimeLog log) {
        return Objects.requireNonNullElse(log.getWorkedMinutes(), 0);   // always set once saved
    }
}
