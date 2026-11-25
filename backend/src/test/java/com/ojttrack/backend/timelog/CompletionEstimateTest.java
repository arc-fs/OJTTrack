package com.ojttrack.backend.timelog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ojttrack.backend.timelog.CompletionEstimate.Status;
import com.ojttrack.backend.timelog.CompletionEstimate.VerifiedDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The estimate is a pure calculation, so these tests need no mocks, clock or database. */
class CompletionEstimateTest {

    // Mon 4 Jan - Fri 8 Jan 2027 plus Mon 11 Jan: 6 logs of 8 h, window Jan 4..11 = 8 calendar days
    static final List<VerifiedDay> SIX_DAYS = List.of(
            day(2027, 1, 4, 480), day(2027, 1, 5, 480), day(2027, 1, 6, 480),
            day(2027, 1, 7, 480), day(2027, 1, 8, 480), day(2027, 1, 11, 480));

    @Test
    void requirementReachedHasNoDate() {
        CompletionEstimate estimate = CompletionEstimate.calculate(SIX_DAYS, 0);

        assertThat(estimate.status()).isEqualTo(Status.REQUIREMENT_REACHED);
        assertThat(estimate.estimatedDate()).isNull();
    }

    @Test
    void noVerifiedLogsIsNotEnoughData() {
        CompletionEstimate estimate = CompletionEstimate.calculate(List.of(), 29160);

        assertThat(estimate.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
        assertThat(estimate.basedOnLogsUpTo()).isNull();
    }

    @Test
    void fewerThanFiveLogsIsNotEnoughData() {
        List<VerifiedDay> four = List.of(day(2027, 1, 4, 480), day(2027, 1, 6, 480),
                day(2027, 1, 8, 480), day(2027, 1, 12, 480));   // spans 9 days, but only 4 logs

        assertThat(CompletionEstimate.calculate(four, 29160).status()).isEqualTo(Status.NOT_ENOUGH_DATA);
    }

    @Test
    void lessThanSevenDaysIsNotEnoughData() {
        List<VerifiedDay> fiveInARow = SIX_DAYS.subList(0, 5);   // Jan 4..8 = 5 days

        CompletionEstimate estimate = CompletionEstimate.calculate(fiveInARow, 29160);

        assertThat(estimate.status()).isEqualTo(Status.NOT_ENOUGH_DATA);
        assertThat(estimate.basedOnLogsUpTo()).isEqualTo(LocalDate.of(2027, 1, 8));
    }

    @Test
    void estimateUsesCalendarDayPace() {
        // H = 2880 min over D = 8 days -> 360 min/day. 1440 remaining -> 4 days after Jan 11.
        CompletionEstimate estimate = CompletionEstimate.calculate(SIX_DAYS, 1440);

        assertThat(estimate.status()).isEqualTo(Status.AVAILABLE);
        assertThat(estimate.estimatedDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(estimate.basedOnLogsUpTo()).isEqualTo(LocalDate.of(2027, 1, 11));
    }

    @Test
    void partialDaysRoundUpNeverOptimistic() {
        // 1441 * 8 / 2880 = 4.003 -> 5 days, not 4
        CompletionEstimate estimate = CompletionEstimate.calculate(SIX_DAYS, 1441);

        assertThat(estimate.estimatedDate()).isEqualTo(LocalDate.of(2027, 1, 16));
    }

    @Test
    void onlyTheLastFourWeeksSetThePace() {
        // A slow start (1 h/day in early December) followed by full 8 h days in January.
        List<VerifiedDay> logs = new ArrayList<>();
        for (int d = 1; d <= 5; d++) {
            logs.add(day(2026, 12, d, 60));
        }
        logs.addAll(SIX_DAYS);

        // Window = Dec 15..Jan 11 (28 days), so December's slow days are ignored:
        // H = 2880, D = 28 -> 2880 remaining needs 28 days -> Feb 8.
        // (Using every log since Dec 1 would give 39 days: a stale, too-slow pace.)
        CompletionEstimate estimate = CompletionEstimate.calculate(logs, 2880);

        assertThat(estimate.estimatedDate()).isEqualTo(LocalDate.of(2027, 2, 8));
    }

    @Test
    void orderOfLogsDoesNotMatter() {
        List<VerifiedDay> shuffled = new ArrayList<>(SIX_DAYS);
        java.util.Collections.reverse(shuffled);

        assertThat(CompletionEstimate.calculate(shuffled, 1440))
                .isEqualTo(CompletionEstimate.calculate(SIX_DAYS, 1440));
    }

    private static VerifiedDay day(int year, int month, int dayOfMonth, int minutes) {
        return new VerifiedDay(LocalDate.of(year, month, dayOfMonth), minutes);
    }
}
