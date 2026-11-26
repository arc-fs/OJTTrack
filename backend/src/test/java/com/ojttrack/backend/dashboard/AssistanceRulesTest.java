package com.ojttrack.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.ojttrack.backend.dashboard.AssistanceFlag.Code;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.timelog.TimeLog;
import com.ojttrack.backend.timelog.TimeLogStatus;
import com.ojttrack.backend.timelog.TimeLogSummaryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The assistance rules are pure functions: plain dates in, flags out. No mocks needed. */
class AssistanceRulesTest {

    static final LocalDate TODAY = LocalDate.of(2027, 1, 20);

    @Nested
    class NoPlacement {
        @Test
        void notFlaggedWhenTheSearchStartIsUnset() {
            assertThat(codes(null, List.of(), List.of())).isEmpty();
        }

        @Test
        void notFlaggedBeforeTheSearchStart() {
            assertThat(codes(TODAY.plusDays(1), List.of(), List.of())).isEmpty();
        }

        @Test
        void flaggedFromTheSearchStartDay() {
            assertThat(codes(TODAY, List.of(), List.of())).containsExactly(Code.NO_PLACEMENT);
        }

        @Test
        void notFlaggedWithACompletedPlacement() {
            Placement done = placement(1, PlacementStatus.COMPLETED, TODAY.minusDays(90), TODAY.minusDays(10));
            assertThat(codes(TODAY.minusDays(100), List.of(done), List.of())).isEmpty();
        }

        @Test
        void notFlaggedWithAnActivePlacement() {
            Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(3), TODAY.plusDays(60));
            assertThat(codes(TODAY.minusDays(30), List.of(active), List.of())).doesNotContain(Code.NO_PLACEMENT);
        }

        @Test
        void aTerminatedPlacementAloneStillCountsAsNoPlacement() {
            Placement ended = placement(1, PlacementStatus.TERMINATED, TODAY.minusDays(60), TODAY.plusDays(30));
            assertThat(codes(TODAY.minusDays(30), List.of(ended), List.of())).containsExactly(Code.NO_PLACEMENT);
        }
    }

    @Nested
    class NoRecentLogs {
        @Test
        void notFlaggedBeforeThePlacementStarts() {
            Placement future = placement(1, PlacementStatus.ACTIVE, TODAY.plusDays(5), TODAY.plusDays(90));
            assertThat(codes(null, List.of(future), List.of())).isEmpty();
        }

        @Test
        void sevenDaysAfterStartWithoutLogsIsNotYetFlagged() {
            Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(7), TODAY.plusDays(90));
            assertThat(codes(null, List.of(active), List.of())).isEmpty();
        }

        @Test
        void eightDaysAfterStartWithoutLogsIsFlagged() {
            Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(8), TODAY.plusDays(90));
            List<AssistanceFlag> flags = AssistanceRules.flagsFor(null, List.of(active), List.of(), TODAY);
            assertThat(flags).extracting(AssistanceFlag::code).containsExactly(Code.NO_RECENT_LOGS);
            assertThat(flags.get(0).message()).isEqualTo("No logs yet since starting on " + TODAY.minusDays(8));
        }

        @Test
        void lastLogSevenDaysAgoIsFineEightDaysIsFlagged() {
            Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(30), TODAY.plusDays(90));
            assertThat(codes(null, List.of(active), List.of(log(1, TODAY.minusDays(7), TimeLogStatus.VERIFIED, 480))))
                    .isEmpty();
            List<AssistanceFlag> flags = AssistanceRules.flagsFor(null, List.of(active),
                    List.of(log(1, TODAY.minusDays(8), TimeLogStatus.VERIFIED, 480)), TODAY);
            assertThat(flags).extracting(AssistanceFlag::code).containsExactly(Code.NO_RECENT_LOGS);
            assertThat(flags.get(0).message()).isEqualTo("No logs since " + TODAY.minusDays(8));
        }

        @Test
        void aPendingOrReturnedLogCountsAsActivity() {
            Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(30), TODAY.plusDays(90));
            assertThat(codes(null, List.of(active), List.of(log(1, TODAY.minusDays(2), TimeLogStatus.PENDING, 480))))
                    .isEmpty();
        }

        @Test
        void logsOfAnOldPlacementDoNotCount() {
            Placement old = placement(1, PlacementStatus.TERMINATED, TODAY.minusDays(60), TODAY.plusDays(30));
            Placement active = placement(2, PlacementStatus.ACTIVE, TODAY.minusDays(10), TODAY.plusDays(90));
            // A recent log, but on the old placement: the new one still has no activity.
            assertThat(codes(null, List.of(old, active), List.of(log(1, TODAY.minusDays(1), TimeLogStatus.VERIFIED, 480))))
                    .containsExactly(Code.NO_RECENT_LOGS);
        }
    }

    @Nested
    class ReturnedPending {
        @Test
        void returnedLogsOnTheActivePlacementAreFlagged() {
            Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(10), TODAY.plusDays(90));
            List<AssistanceFlag> flags = AssistanceRules.flagsFor(null, List.of(active), List.of(
                    log(1, TODAY.minusDays(1), TimeLogStatus.RETURNED, 480),
                    log(1, TODAY.minusDays(2), TimeLogStatus.RETURNED, 480)), TODAY);
            assertThat(flags).extracting(AssistanceFlag::code).containsExactly(Code.RETURNED_PENDING);
            assertThat(flags.get(0).message()).startsWith("2 returned log(s)");
        }

        @Test
        void returnedLogsOnATerminatedPlacementAreNotFlagged() {
            Placement old = placement(1, PlacementStatus.TERMINATED, TODAY.minusDays(60), TODAY.plusDays(30));
            Placement active = placement(2, PlacementStatus.ACTIVE, TODAY.minusDays(3), TODAY.plusDays(90));
            assertThat(codes(null, List.of(old, active), List.of(log(1, TODAY.minusDays(40), TimeLogStatus.RETURNED, 480))))
                    .doesNotContain(Code.RETURNED_PENDING);
        }
    }

    @Nested
    class BehindSchedule {
        // 6 verified 8-hour logs, Jan 4-8 and Jan 11: a reliable estimate (>= 5 logs over >= 7 days).
        // 486 h required -> estimated 2027-03-25 (see CompletionEstimate).
        List<TimeLog> sixVerifiedDays() {
            List<TimeLog> logs = new ArrayList<>();
            for (int day : new int[] {4, 5, 6, 7, 8, 11}) {
                logs.add(log(1, LocalDate.of(2027, 1, day), TimeLogStatus.VERIFIED, 480));
            }
            return logs;
        }

        LocalDate estimate(Placement placement, List<TimeLog> logs) {
            return TimeLogSummaryResponse.of(placement, logs).estimatedCompletionDate();
        }

        @Test
        void flaggedWhenTheReliableEstimateIsAfterThePlannedEnd() {
            Placement active = placement(1, PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 31));
            List<AssistanceFlag> flags = AssistanceRules.flagsFor(null, List.of(active), sixVerifiedDays(),
                    LocalDate.of(2027, 1, 12));
            assertThat(flags).extracting(AssistanceFlag::code).containsExactly(Code.BEHIND_SCHEDULE);
            assertThat(flags.get(0).message()).contains("after the planned end 2027-01-31");
        }

        @Test
        void notFlaggedWhenTheEstimateIsOnThePlannedEnd() {
            Placement active = placement(1, PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 31));
            ReflectionTestUtils.setField(active, "endDate", estimate(active, sixVerifiedDays()));
            assertThat(codes(null, List.of(active), sixVerifiedDays(), LocalDate.of(2027, 1, 12))).isEmpty();
        }

        @Test
        void flaggedWhenTheEstimateIsOneDayAfterThePlannedEnd() {
            Placement active = placement(1, PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 31));
            LocalDate estimated = estimate(active, sixVerifiedDays());
            ReflectionTestUtils.setField(active, "endDate", estimated.minusDays(1));
            assertThat(codes(null, List.of(active), sixVerifiedDays(), LocalDate.of(2027, 1, 12)))
                    .containsExactly(Code.BEHIND_SCHEDULE);
        }

        @Test
        void notFlaggedWithoutEnoughVerifiedData() {
            Placement active = placement(1, PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));
            List<TimeLog> four = sixVerifiedDays().subList(0, 4);   // only 4 logs: NOT_ENOUGH_DATA
            assertThat(codes(null, List.of(active), four, LocalDate.of(2027, 1, 12))).doesNotContain(Code.BEHIND_SCHEDULE);
        }

        @Test
        void notFlaggedWhenTheRequirementIsReached() {
            Placement active = placement(1, PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 5));
            ReflectionTestUtils.setField(active, "requiredHours", 8);
            assertThat(codes(null, List.of(active), sixVerifiedDays(), LocalDate.of(2027, 1, 12))).isEmpty();
        }

        @Test
        void notFlaggedWithoutAPlannedEndDate() {
            Placement active = placement(1, PlacementStatus.ACTIVE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 31));
            ReflectionTestUtils.setField(active, "endDate", null);
            assertThat(codes(null, List.of(active), sixVerifiedDays(), LocalDate.of(2027, 1, 12))).isEmpty();
        }
    }

    @Test
    void everyApplicableReasonIsReturned() {
        Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(30), TODAY.plusDays(90));
        List<TimeLog> logs = List.of(log(1, TODAY.minusDays(20), TimeLogStatus.RETURNED, 480));
        assertThat(codes(null, List.of(active), logs)).containsExactly(Code.NO_RECENT_LOGS, Code.RETURNED_PENDING);
    }

    @Test
    void currentPlacementPrefersActiveThenLatestCompleted() {
        Placement olderDone = placement(1, PlacementStatus.COMPLETED, TODAY.minusDays(200), TODAY.minusDays(100));
        ReflectionTestUtils.setField(olderDone, "endedAt", Instant.parse("2026-06-01T00:00:00Z"));
        Placement newerDone = placement(2, PlacementStatus.COMPLETED, TODAY.minusDays(90), TODAY.minusDays(10));
        ReflectionTestUtils.setField(newerDone, "endedAt", Instant.parse("2026-12-01T00:00:00Z"));
        Placement active = placement(3, PlacementStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(60));

        assertThat(AssistanceRules.currentPlacement(List.of(olderDone, newerDone))).contains(newerDone);
        assertThat(AssistanceRules.currentPlacement(List.of(olderDone, active, newerDone))).contains(active);
        assertThat(AssistanceRules.currentPlacement(List.of())).isEmpty();
    }

    // ---- helpers ----

    private static List<Code> codes(LocalDate searchStart, List<Placement> placements, List<TimeLog> logs) {
        return codes(searchStart, placements, logs, TODAY);
    }

    private static List<Code> codes(LocalDate searchStart, List<Placement> placements, List<TimeLog> logs, LocalDate today) {
        return AssistanceRules.flagsFor(searchStart, placements, logs, today).stream().map(AssistanceFlag::code).toList();
    }

    static Placement placement(int id, PlacementStatus status, LocalDate start, LocalDate end) {
        Placement placement = Placement.activate(100 + id, 10, start, end, 486);
        ReflectionTestUtils.setField(placement, "id", id);
        ReflectionTestUtils.setField(placement, "status", status);
        if (status != PlacementStatus.ACTIVE) {
            ReflectionTestUtils.setField(placement, "endedAt", Instant.parse("2026-12-01T00:00:00Z"));
        }
        return placement;
    }

    static TimeLog log(int placementId, LocalDate date, TimeLogStatus status, int minutes) {
        TimeLog log = TimeLog.submit(placementId, 10, date, LocalTime.of(8, 0), LocalTime.of(17, 0), false, 60, "work");
        ReflectionTestUtils.setField(log, "status", status);
        ReflectionTestUtils.setField(log, "workedMinutes", minutes);   // normally calculated by PostgreSQL
        return log;
    }
}
