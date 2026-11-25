package com.ojttrack.backend.timelog;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Estimated completion date for a placement, from the student's recent VERIFIED pace.
 * Calculated whenever it is shown and never stored: it is a prediction, not a record.
 *
 * Definition (agreed in the time logs plan):
 *   L = latest verified log date; S = max(first verified date, L - 27 days)
 *   D = days in [S, L], inclusive (1..28);  H = verified minutes dated in [S, L]
 *   pace = H / D minutes per calendar day (calendar days, so weekends, holidays and
 *   absences are included automatically)
 *   estimate = L + ceil(remaining * D / H) days
 * It needs at least 5 verified logs in the window spanning 7+ days; otherwise
 * NOT_ENOUGH_DATA. It never uses "today", so it is deterministic.
 */
public record CompletionEstimate(Status status, LocalDate estimatedDate, LocalDate basedOnLogsUpTo) {

    public enum Status { AVAILABLE, NOT_ENOUGH_DATA, REQUIREMENT_REACHED }

    /** One VERIFIED log: its date (the shift's start day) and worked minutes. */
    public record VerifiedDay(LocalDate date, int minutes) {}

    static final int WINDOW_DAYS = 28;
    static final int MIN_LOGS = 5;
    static final int MIN_SPAN_DAYS = 7;

    public static CompletionEstimate calculate(List<VerifiedDay> verified, long remainingMinutes) {
        LocalDate latest = verified.stream().map(VerifiedDay::date).max(Comparator.naturalOrder()).orElse(null);
        if (remainingMinutes <= 0) {
            return new CompletionEstimate(Status.REQUIREMENT_REACHED, null, latest);
        }
        if (latest == null) {
            return new CompletionEstimate(Status.NOT_ENOUGH_DATA, null, null);
        }

        LocalDate first = verified.stream().map(VerifiedDay::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate windowStart = first.isAfter(latest.minusDays(WINDOW_DAYS - 1)) ? first : latest.minusDays(WINDOW_DAYS - 1);
        long spanDays = ChronoUnit.DAYS.between(windowStart, latest) + 1;

        List<VerifiedDay> inWindow = verified.stream().filter(day -> !day.date().isBefore(windowStart)).toList();
        long windowMinutes = inWindow.stream().mapToLong(VerifiedDay::minutes).sum();

        if (inWindow.size() < MIN_LOGS || spanDays < MIN_SPAN_DAYS || windowMinutes <= 0) {
            return new CompletionEstimate(Status.NOT_ENOUGH_DATA, null, latest);
        }
        // ceil(remaining * D / H) in whole numbers: always rounds up, never optimistic.
        long daysNeeded = (remainingMinutes * spanDays + windowMinutes - 1) / windowMinutes;
        return new CompletionEstimate(Status.AVAILABLE, latest.plusDays(daysNeeded), latest);
    }
}
