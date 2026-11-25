package com.ojttrack.backend.timelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Student-side time log rules. Repositories are mocks; the clock is fixed in Manila time. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimeLogServiceTest {

    static final int STUDENT = 10;
    static final int OTHER_STUDENT = 11;
    static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    static final LocalDate START = LocalDate.of(2027, 1, 4);
    static final LocalDate PLANNED_END = LocalDate.of(2027, 1, 8);
    // 2027-01-10 10:00 in Manila
    static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-10T02:00:00Z"), MANILA);

    @Mock TimeLogRepository logs;
    @Mock PlacementRepository placements;
    @Mock AppUserRepository users;

    TimeLogService service;
    Placement active;

    @BeforeEach
    void setUp() {
        service = new TimeLogService(logs, placements, users, CLOCK);
        active = placement(500, STUDENT);
        when(placements.findForUpdateByStudentIdAndStatus(STUDENT, PlacementStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(placements.findByStudentIdAndStatus(STUDENT, PlacementStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(placements.findByIdForUpdate(500)).thenReturn(Optional.of(active));
        when(logs.saveAndFlush(any(TimeLog.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- submit ----

    @Test
    void submitSavesPendingLogOnTheActivePlacement() {
        TimeLogResponse response = service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "08:00", "17:00", false, 60));

        ArgumentCaptor<TimeLog> saved = ArgumentCaptor.forClass(TimeLog.class);
        verify(logs).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPlacementId()).isEqualTo(500);
        assertThat(saved.getValue().getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getValue().getAccomplishments()).isEqualTo("Built the login page");   // trimmed
        assertThat(response.status()).isEqualTo(TimeLogStatus.PENDING);
    }

    @Test
    void submitWithoutActivePlacementIsAConflict() {
        when(placements.findForUpdateByStudentIdAndStatus(STUDENT, PlacementStatus.ACTIVE)).thenReturn(Optional.empty());

        assertStatus(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "08:00", "17:00", false, 60)),
                HttpStatus.CONFLICT);
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void submitRejectsFutureDate() {
        assertStatus(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 11), "08:00", "17:00", false, 60)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void todayFollowsManilaTimeNotUtc() {
        // 2027-01-10 15:59 UTC = 23:59 on Jan 10 in Manila: Jan 11 is still in the future.
        TimeLogService beforeMidnight = new TimeLogService(logs, placements, users,
                Clock.fixed(Instant.parse("2027-01-10T15:59:00Z"), MANILA));
        assertStatus(() -> beforeMidnight.submit(STUDENT, request(LocalDate.of(2027, 1, 11), "08:00", "17:00", false, 60)),
                HttpStatus.BAD_REQUEST);

        // 2027-01-10 16:00 UTC = 00:00 on Jan 11 in Manila, even though UTC is still Jan 10.
        TimeLogService afterMidnight = new TimeLogService(logs, placements, users,
                Clock.fixed(Instant.parse("2027-01-10T16:00:00Z"), MANILA));
        afterMidnight.submit(STUDENT, request(LocalDate.of(2027, 1, 11), "08:00", "17:00", false, 60));
        verify(logs).saveAndFlush(any());
    }

    @Test
    void submitRejectsDateBeforePlacementStart() {
        assertStatus(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 3), "08:00", "17:00", false, 60)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitAllowsDateAfterPlannedEnd() {
        assertThat(LocalDate.of(2027, 1, 9)).isAfter(PLANNED_END);
        service.submit(STUDENT, request(LocalDate.of(2027, 1, 9), "08:00", "17:00", false, 60));
        verify(logs).saveAndFlush(any());
    }

    @Test
    void submitRejectsSecondLogForTheSameDay() {
        when(logs.existsByPlacementIdAndLogDate(500, LocalDate.of(2027, 1, 6))).thenReturn(true);

        assertStatus(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "13:00", "17:00", false, 0)),
                HttpStatus.CONFLICT);
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void overnightTimesWithoutTheFlagAreRejected() {
        assertMessage(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "22:00", "06:00", false, 60)),
                "Ends next day");
    }

    @Test
    void flagWithSameDayTimesIsRejected() {
        assertStatus(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "08:00", "17:00", true, 60)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void overnightShiftWithTheFlagIsAccepted() {
        service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "22:00", "06:00", true, 60));
        verify(logs).saveAndFlush(any());
    }

    @Test
    void sixteenHourShiftIsTheMaximum() {
        // 20:00 -> 12:00 next day = exactly 960 minutes: allowed
        service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "20:00", "12:00", true, 0));
        verify(logs).saveAndFlush(any());

        // 20:00 -> 12:01 next day = 961 minutes: rejected
        assertMessage(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 7), "20:00", "12:01", true, 0)),
                "16 hours");
    }

    @Test
    void breakAsLongAsTheShiftIsRejected() {
        assertMessage(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "08:00", "12:00", false, 240)),
                "Break must be shorter");
    }

    @Test
    void equalTimesAreRejected() {
        assertStatus(() -> service.submit(STUDENT, request(LocalDate.of(2027, 1, 6), "08:00", "08:00", false, 0)),
                HttpStatus.BAD_REQUEST);
    }

    // ---- edit ----

    @Test
    void editOfAnotherStudentsLogIsNotFound() {
        when(logs.findByIdAndStudentId(7, STUDENT)).thenReturn(Optional.empty());

        assertStatus(() -> service.edit(STUDENT, 7, editRequest(0)), HttpStatus.NOT_FOUND);
    }

    @Test
    void editRequiresVersion() {
        assertStatus(() -> service.edit(STUDENT, 7, editRequest(null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void editWithStaleVersionIsAConflict() {
        givenLog(7, TimeLogStatus.PENDING, 3);

        assertMessage(() -> service.edit(STUDENT, 7, editRequest(2)), "changed since you loaded it");
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void verifiedLogCannotBeEdited() {
        givenLog(7, TimeLogStatus.VERIFIED, 1);

        assertMessage(() -> service.edit(STUDENT, 7, editRequest(1)), "Verified logs can't be edited");
    }

    @Test
    void logsOfAnEndedPlacementAreFrozen() {
        givenLog(7, TimeLogStatus.RETURNED, 1);
        active.terminate();

        assertMessage(() -> service.edit(STUDENT, 7, editRequest(1)), "placement has ended");
    }

    @Test
    void editingAReturnedLogResubmitsItAsPending() {
        TimeLog log = givenLog(7, TimeLogStatus.RETURNED, 1);
        ReflectionTestUtils.setField(log, "feedback", "Please add details");

        TimeLogResponse response = service.edit(STUDENT, 7, editRequest(1));

        assertThat(log.getStatus()).isEqualTo(TimeLogStatus.PENDING);
        assertThat(log.getBreakMinutes()).isEqualTo(30);
        assertThat(response.feedback()).isEqualTo("Please add details");   // still visible after resubmitting
    }

    @Test
    void editToADateThatAlreadyHasALogIsAConflict() {
        givenLog(7, TimeLogStatus.PENDING, 0);
        when(logs.existsByPlacementIdAndLogDateAndIdNot(500, LocalDate.of(2027, 1, 7), 7)).thenReturn(true);

        assertStatus(() -> service.edit(STUDENT, 7, editRequest(0)), HttpStatus.CONFLICT);
    }

    // ---- list ----

    @Test
    void listOfAnotherStudentsPlacementIsNotFound() {
        Placement theirs = placement(600, OTHER_STUDENT);
        when(placements.findById(600)).thenReturn(Optional.of(theirs));

        assertStatus(() -> service.list(STUDENT, 600), HttpStatus.NOT_FOUND);
        verify(logs, never()).findByPlacementIdOrderByLogDateDesc(any());
    }

    @Test
    void listWithoutActivePlacementIsEmpty() {
        when(placements.findByStudentIdAndStatus(STUDENT, PlacementStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThat(service.list(STUDENT, null)).isEmpty();
    }

    @Test
    void listDefaultsToTheActivePlacement() {
        when(logs.findByPlacementIdOrderByLogDateDesc(500)).thenReturn(List.of());

        service.list(STUDENT, null);

        verify(logs).findByPlacementIdOrderByLogDateDesc(500);
    }

    // ---- helpers ----

    private static TimeLogRequest request(LocalDate date, String in, String out, boolean nextDay, int breakMinutes) {
        return new TimeLogRequest(date, LocalTime.parse(in), LocalTime.parse(out), nextDay, breakMinutes,
                "  Built the login page  ", null);
    }

    private static TimeLogRequest editRequest(Integer version) {
        return new TimeLogRequest(LocalDate.of(2027, 1, 7), LocalTime.parse("08:00"), LocalTime.parse("17:00"),
                false, 30, "Fixed bugs", version);
    }

    private TimeLog givenLog(int id, TimeLogStatus status, int version) {
        TimeLog log = TimeLog.submit(500, STUDENT, LocalDate.of(2027, 1, 6),
                LocalTime.parse("08:00"), LocalTime.parse("17:00"), false, 60, "Work");
        ReflectionTestUtils.setField(log, "id", id);
        ReflectionTestUtils.setField(log, "status", status);
        ReflectionTestUtils.setField(log, "version", version);
        when(logs.findByIdAndStudentId(id, STUDENT)).thenReturn(Optional.of(log));
        return log;
    }

    private static Placement placement(int id, int studentId) {
        Placement placement = Placement.activate(100, studentId, START, PLANNED_END, 486);
        ReflectionTestUtils.setField(placement, "id", id);
        return placement;
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
