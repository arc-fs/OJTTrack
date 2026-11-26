package com.ojttrack.backend.timelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Coordinator review and completion rules. Repositories are mocks; the clock is fixed. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeLogReviewServiceTest {

    static final int COORDINATOR = 1;
    static final int OTHER_COORDINATOR = 2;
    static final int STUDENT = 10;
    static final Instant NOW = Instant.parse("2027-01-10T02:00:00Z");

    @Mock TimeLogRepository logs;
    @Mock PlacementRepository placements;
    @Mock JobApplicationRepository applications;
    @Mock AppUserRepository users;

    TimeLogReviewService service;
    Placement placement;

    @BeforeEach
    void setUp() {
        service = new TimeLogReviewService(logs, placements, applications, users,
                Clock.fixed(NOW, ZoneId.of("Asia/Manila")));
        placement = Placement.activate(100, STUDENT, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 4, 30), 8, "Intern");
        ReflectionTestUtils.setField(placement, "id", 500);
        when(placements.findByIdForUpdate(500)).thenReturn(Optional.of(placement));
        when(placements.findById(500)).thenReturn(Optional.of(placement));

        AppUser coordinator = AppUser.coordinator("c@test.local", "hash", "Coach Cruz");
        ReflectionTestUtils.setField(coordinator, "id", COORDINATOR);
        when(users.findById(COORDINATOR)).thenReturn(Optional.of(coordinator));
        when(users.findById(STUDENT)).thenReturn(Optional.of(TestUsers.studentEntity(STUDENT, COORDINATOR)));

        when(logs.saveAndFlush(any(TimeLog.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- verify ----

    @Test
    void verifyRecordsCoordinatorAndTime() {
        TimeLog log = givenLog(7, TimeLogStatus.PENDING, 2, 480);

        TimeLogResponse response = service.verify(COORDINATOR, 7, 2);

        assertThat(log.getStatus()).isEqualTo(TimeLogStatus.VERIFIED);
        assertThat(log.getVerifiedBy()).isEqualTo(COORDINATOR);
        assertThat(log.getVerifiedAt()).isEqualTo(NOW);
        assertThat(response.verifiedByName()).isEqualTo("Coach Cruz");
    }

    @Test
    void anotherCoordinatorCannotVerify() {
        givenLog(7, TimeLogStatus.PENDING, 2, 480);

        assertStatus(() -> service.verify(OTHER_COORDINATOR, 7, 2), HttpStatus.NOT_FOUND);
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void verifyWithStaleVersionIsAConflict() {
        givenLog(7, TimeLogStatus.PENDING, 3, 480);   // student edited it after the coordinator loaded version 2

        assertMessage(() -> service.verify(COORDINATOR, 7, 2), "changed since you loaded it");
    }

    @Test
    void onlyPendingLogsCanBeVerified() {
        givenLog(7, TimeLogStatus.RETURNED, 2, 480);

        assertStatus(() -> service.verify(COORDINATOR, 7, 2), HttpStatus.CONFLICT);
    }

    @Test
    void logsOfEndedPlacementsCannotBeReviewed() {
        givenLog(7, TimeLogStatus.PENDING, 2, 480);
        placement.terminate();

        assertMessage(() -> service.verify(COORDINATOR, 7, 2), "placement has ended");
    }

    // ---- return ----

    @Test
    void returnStoresTrimmedFeedback() {
        TimeLog log = givenLog(7, TimeLogStatus.PENDING, 2, 480);

        service.returnLog(COORDINATOR, 7, 2, "  Please list what you built  ");

        assertThat(log.getStatus()).isEqualTo(TimeLogStatus.RETURNED);
        assertThat(log.getFeedback()).isEqualTo("Please list what you built");
    }

    // ---- un-verify ----

    @Test
    void unverifyReturnsTheLogWithTheReasonAndClearsVerification() {
        TimeLog log = givenLog(7, TimeLogStatus.VERIFIED, 4, 480);
        ReflectionTestUtils.setField(log, "verifiedBy", COORDINATOR);
        ReflectionTestUtils.setField(log, "verifiedAt", NOW);

        TimeLogResponse response = service.unverify(COORDINATOR, 7, 4, "  Wrong time out  ");

        assertThat(log.getStatus()).isEqualTo(TimeLogStatus.RETURNED);
        assertThat(log.getFeedback()).isEqualTo("Wrong time out");   // what the student will see
        assertThat(log.getVerifiedBy()).isNull();
        assertThat(log.getVerifiedAt()).isNull();
        assertThat(response.feedback()).isEqualTo("Wrong time out");
    }

    @Test
    void onlyVerifiedLogsCanBeUnverified() {
        givenLog(7, TimeLogStatus.PENDING, 2, 480);

        assertStatus(() -> service.unverify(COORDINATOR, 7, 2, "reason"), HttpStatus.CONFLICT);
    }

    @Test
    void anotherCoordinatorCannotUnverify() {
        givenLog(7, TimeLogStatus.VERIFIED, 2, 480);

        assertStatus(() -> service.unverify(OTHER_COORDINATOR, 7, 2, "reason"), HttpStatus.NOT_FOUND);
    }

    @Test
    void verifiedLogsOfACompletedPlacementCannotBeUnverified() {
        givenLog(7, TimeLogStatus.VERIFIED, 2, 480);
        placement.complete(COORDINATOR);

        assertStatus(() -> service.unverify(COORDINATOR, 7, 2, "reason"), HttpStatus.CONFLICT);
    }

    // ---- completion ----

    @Test
    void cannotCompleteWhileLogsArePending() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 480), log(TimeLogStatus.PENDING, 60));

        assertMessage(() -> service.complete(COORDINATOR, 500), "still pending");
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
    }

    @Test
    void cannotCompleteBelowTheRequiredHours() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 479));   // 8 h required = 480 min

        assertMessage(() -> service.complete(COORDINATOR, 500), "not been reached");
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
    }

    @Test
    void returnedLogsDoNotCountAndDoNotBlockCompletion() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 480), log(TimeLogStatus.RETURNED, 300));

        TimeLogSummaryResponse result = service.complete(COORDINATOR, 500);

        assertThat(result.verifiedMinutes()).isEqualTo(480);
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.COMPLETED);
    }

    @Test
    void completingWithExactlyTheRequiredHoursRecordsTheCoordinator() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 480));

        service.complete(COORDINATOR, 500);

        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.COMPLETED);
        assertThat(placement.getCompletedBy()).isEqualTo(COORDINATOR);
        assertThat(placement.getEndedAt()).isNotNull();
    }

    @Test
    void anotherCoordinatorCannotComplete() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 480));

        assertStatus(() -> service.complete(OTHER_COORDINATOR, 500), HttpStatus.NOT_FOUND);
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
    }

    // ---- summary ----

    @Test
    void summaryCountsOnlyVerifiedMinutesTowardTheRequirement() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 300), log(TimeLogStatus.PENDING, 120),
                log(TimeLogStatus.RETURNED, 60));

        TimeLogSummaryResponse summary = service.summary(COORDINATOR, 500);

        assertThat(summary.requiredMinutes()).isEqualTo(480);
        assertThat(summary.verifiedMinutes()).isEqualTo(300);
        assertThat(summary.pendingMinutes()).isEqualTo(120);
        assertThat(summary.pendingCount()).isEqualTo(1);
        assertThat(summary.returnedCount()).isEqualTo(1);
        assertThat(summary.remainingMinutes()).isEqualTo(180);
        assertThat(summary.requirementReached()).isFalse();
        // reaching the hours is only displayed; completion stays a separate coordinator action
        verify(placements, never()).saveAndFlush(any());
    }

    @Test
    void remainingNeverGoesNegative() {
        givenPlacementLogs(log(TimeLogStatus.VERIFIED, 900));

        TimeLogSummaryResponse summary = service.summary(COORDINATOR, 500);

        assertThat(summary.remainingMinutes()).isZero();
        assertThat(summary.requirementReached()).isTrue();
        assertThat(placement.getStatus()).isEqualTo(PlacementStatus.ACTIVE);   // never automatic
    }

    @Test
    void anotherCoordinatorCannotSeeTheSummary() {
        assertStatus(() -> service.summary(OTHER_COORDINATOR, 500), HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    private TimeLog givenLog(int id, TimeLogStatus status, int version, int minutes) {
        TimeLog log = log(status, minutes);
        ReflectionTestUtils.setField(log, "id", id);
        ReflectionTestUtils.setField(log, "version", version);
        when(logs.findById(id)).thenReturn(Optional.of(log));
        return log;
    }

    private static TimeLog log(TimeLogStatus status, int workedMinutes) {
        TimeLog log = TimeLog.submit(500, STUDENT, LocalDate.of(2027, 1, 6),
                LocalTime.parse("08:00"), LocalTime.parse("17:00"), false, 60, "work");
        ReflectionTestUtils.setField(log, "status", status);
        ReflectionTestUtils.setField(log, "workedMinutes", workedMinutes);   // normally set by PostgreSQL
        return log;
    }

    private void givenPlacementLogs(TimeLog... placementLogs) {
        when(logs.findByPlacementIdOrderByLogDateDesc(500)).thenReturn(List.of(placementLogs));
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo((HttpStatusCode) expected);
    }

    private static void assertMessage(Runnable action, String expectedText) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getReason()).contains(expectedText));
    }
}
