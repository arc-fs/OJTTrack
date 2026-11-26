package com.ojttrack.backend.timelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.config.TimeConfig;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementRequest;
import com.ojttrack.backend.placement.PlacementRequestRepository;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proves what only PostgreSQL can: the generated worked_minutes column and the V5
 * CHECK constraints. Constraint tests insert raw SQL on purpose, to show the database
 * rejects bad rows even if Java validation were bypassed or buggy.
 *
 * Needs ojttrack_test (see application-dbtest.properties). From backend/:
 *   .\mvnw.cmd test "-Dtest=*DbTest"   (with DB_PASSWORD set)
 * Each test is rolled back. A rejected statement aborts the transaction, so it is
 * always the last statement in its test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dbtest")
@Import({TimeLogService.class, TimeLogReviewService.class, TimeConfig.class})
class TimeLogDbTest {

    static final LocalDate DAY = LocalDate.of(2027, 1, 6);

    @Autowired AppUserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired PlacementRepository placements;
    @Autowired PlacementRequestRepository requests;
    @Autowired TimeLogRepository logs;
    @Autowired TimeLogService studentService;
    @Autowired TimeLogReviewService reviewService;
    @Autowired EntityManager em;

    AppUser coordinator;
    AppUser student;
    AppUser otherStudent;
    Placement placement;

    @BeforeEach
    void setUp() {
        coordinator = users.save(AppUser.coordinator("tl-coord@test.local", "hash", "TL Coordinator"));
        student = users.save(AppUser.student("tl-s1@test.local", "hash", "TL Student", coordinator.getId()));
        otherStudent = users.save(AppUser.student("tl-s2@test.local", "hash", "TL Other", coordinator.getId()));
        JobApplication app = BeanUtils.instantiateClass(JobApplication.class);   // protected JPA constructor
        app.setStudentId(student.getId());
        app.setCompanyName("Acme");
        app.setStatus(ApplicationStatus.ACCEPTED);
        applications.saveAndFlush(app);
        placement = placements.saveAndFlush(Placement.activate(app.getId(), student.getId(),
                LocalDate.of(2027, 1, 4), LocalDate.of(2027, 4, 30), 486));
    }

    // ---- worked_minutes is calculated by PostgreSQL ----

    @Test
    void dayShiftMinutesAreCalculatedByTheDatabase() {
        TimeLog saved = logs.saveAndFlush(log(DAY, "08:00", "17:00", false, 60));

        assertThat(saved.getWorkedMinutes()).isEqualTo(480);   // 9 h - 60 min break
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void overnightShiftMinutesAreCalculatedByTheDatabase() {
        TimeLog saved = logs.saveAndFlush(log(DAY, "22:00", "06:00", true, 60));

        assertThat(saved.getWorkedMinutes()).isEqualTo(420);   // 8 h - 60 min break
    }

    @Test
    void editRecalculatesMinutesAndIncrementsVersion() {
        TimeLog saved = logs.saveAndFlush(log(DAY, "08:00", "17:00", false, 60));

        saved.edit(DAY, LocalTime.parse("08:00"), LocalTime.parse("12:00"), false, 0, "Half day");
        TimeLog edited = logs.saveAndFlush(saved);

        assertThat(edited.getWorkedMinutes()).isEqualTo(240);
        assertThat(edited.getVersion()).isEqualTo(1);
    }

    // ---- relationships and uniqueness ----

    @Test
    void secondLogForTheSameDayIsRejected() {
        logs.saveAndFlush(log(DAY, "08:00", "12:00", false, 0));

        assertThatThrownBy(() -> logs.saveAndFlush(log(DAY, "13:00", "17:00", false, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);   // ux_time_logs_one_per_day
    }

    @Test
    void logStudentMustBeThePlacementsStudent() {
        TimeLog wrong = TimeLog.submit(placement.getId(), otherStudent.getId(), DAY,
                LocalTime.parse("08:00"), LocalTime.parse("17:00"), false, 60, "work");

        assertThatThrownBy(() -> logs.saveAndFlush(wrong))
                .isInstanceOf(DataIntegrityViolationException.class);   // fk_time_logs_placement_student
    }

    // ---- CHECK constraints, using raw SQL that skips the Java validation ----

    @Test
    void overnightTimesWithoutTheFlagAreRejected() {
        assertRejectedBy("chk_time_logs_direction", () -> insert("22:00", "06:00", false, 0, "'PENDING'", "NULL", "NULL", "NULL"));
    }

    @Test
    void equalTimesAreRejected() {
        assertRejectedBy("chk_time_logs_direction", () -> insert("08:00", "08:00", false, 0, "'PENDING'", "NULL", "NULL", "NULL"));
    }

    @Test
    void secondsAreRejected() {
        assertRejectedBy("chk_time_logs_whole_minutes", () -> insert("08:00:30", "17:00", false, 0, "'PENDING'", "NULL", "NULL", "NULL"));
    }

    @Test
    void sixteenHoursIsTheLongestShift() {
        insert("20:00", "12:00", true, 0, "'PENDING'", "NULL", "NULL", "NULL");   // exactly 960 min: accepted
        assertRejectedBy("chk_time_logs_max_shift",
                () -> insertOn("2027-01-07", "20:00", "12:01", true, 0, "'PENDING'", "NULL", "NULL", "NULL"));
    }

    @Test
    void breakAsLongAsTheShiftIsRejected() {
        assertRejectedBy("chk_time_logs_worked", () -> insert("08:00", "12:00", false, 240, "'PENDING'", "NULL", "NULL", "NULL"));
    }

    @Test
    void verifiedNeedsBothVerificationFields() {
        assertRejectedBy("chk_time_logs_verified",
                () -> insert("08:00", "17:00", false, 60, "'VERIFIED'", "NULL", String.valueOf(coordinator.getId()), "NULL"));
    }

    @Test
    void nonVerifiedLogCannotHaveVerificationFields() {
        assertRejectedBy("chk_time_logs_verified",
                () -> insert("08:00", "17:00", false, 60, "'PENDING'", "NULL", "NULL", "now()"));
    }

    @Test
    void returnedNeedsRealFeedbackText() {
        assertRejectedBy("chk_time_logs_returned_feedback",
                () -> insert("08:00", "17:00", false, 60, "'RETURNED'", "E' \\t\\n '", "NULL", "NULL"));
    }

    @Test
    void decidedPlacementRequestNeedsBothDecisionFields() {
        // V5 replaced the V4 constraint that allowed only one of the two fields.
        PlacementRequest request = requests.saveAndFlush(
                PlacementRequest.submit(student.getId(), placement.getApplicationId(), null, null));

        assertRejectedBy("chk_placement_requests_decision", () -> em.createNativeQuery(
                "UPDATE placement_requests SET status = 'APPROVED', decided_by = " + coordinator.getId()
                        + " WHERE id = " + request.getId()).executeUpdate());
    }

    // ---- review workflow through the real services (every state must satisfy the V5 CHECKs) ----

    @Test
    void verifyThenUnverifyStoresTheReasonAndClearsVerification() {
        TimeLog saved = logs.saveAndFlush(log(DAY, "08:00", "17:00", false, 60));

        reviewService.verify(coordinator.getId(), saved.getId(), saved.getVersion());
        TimeLog verified = reload(saved.getId());
        assertThat(verified.getStatus()).isEqualTo(TimeLogStatus.VERIFIED);
        assertThat(verified.getVerifiedBy()).isEqualTo(coordinator.getId());
        assertThat(verified.getVerifiedAt()).isNotNull();

        reviewService.unverify(coordinator.getId(), saved.getId(), verified.getVersion(), "  Wrong time out  ");
        TimeLog returned = reload(saved.getId());
        assertThat(returned.getStatus()).isEqualTo(TimeLogStatus.RETURNED);
        assertThat(returned.getFeedback()).isEqualTo("Wrong time out");
        assertThat(returned.getVerifiedBy()).isNull();
        assertThat(returned.getVerifiedAt()).isNull();
    }

    @Test
    void returnedLogShowsFeedbackToTheStudent() {
        TimeLog saved = logs.saveAndFlush(log(DAY, "08:00", "17:00", false, 60));

        reviewService.returnLog(coordinator.getId(), saved.getId(), saved.getVersion(), "Add your tasks");
        em.flush();
        em.clear();

        assertThat(studentService.list(student.getId(), null))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.status()).isEqualTo(TimeLogStatus.RETURNED);
                    assertThat(l.feedback()).isEqualTo("Add your tasks");
                });
    }

    @Test
    void completionRecordsTheCoordinator() {
        Placement small = switchToNewPlacement(8);   // 8 required hours = 480 min
        TimeLog saved = logs.saveAndFlush(TimeLog.submit(small.getId(), student.getId(), DAY,
                LocalTime.parse("08:00"), LocalTime.parse("16:00"), false, 0, "work"));
        reviewService.verify(coordinator.getId(), saved.getId(), saved.getVersion());

        reviewService.complete(coordinator.getId(), small.getId());
        em.flush();
        em.clear();

        Placement completed = placements.findById(small.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(com.ojttrack.backend.placement.PlacementStatus.COMPLETED);
        assertThat(completed.getCompletedBy()).isEqualTo(coordinator.getId());
        assertThat(completed.getEndedAt()).isNotNull();
    }

    @Test
    void previousPlacementHoursNeverCountTowardTheNewPlacement() {
        TimeLog old = logs.saveAndFlush(log(DAY, "08:00", "17:00", false, 60));
        reviewService.verify(coordinator.getId(), old.getId(), old.getVersion());
        Integer oldPlacementId = placement.getId();

        switchToNewPlacement(486);
        em.flush();
        em.clear();

        TimeLogSummaryResponse current = studentService.summary(student.getId(), null);
        assertThat(current.verifiedMinutes()).isZero();
        assertThat(current.remainingMinutes()).isEqualTo(486 * 60);

        // ...while the old placement keeps its history
        assertThat(studentService.summary(student.getId(), oldPlacementId).verifiedMinutes()).isEqualTo(480);
    }

    @Test
    void logForAPlacementEndedByATransferIsRejectedAndNothingIsSaved() {
        // The student opened the form for the current placement...
        Integer formOpenedFor = placement.getId();
        // ...then the coordinator approved a transfer before the student pressed Save.
        Placement newPlacement = switchToNewPlacement(486);

        assertThatThrownBy(() -> studentService.submit(student.getId(), new CreateTimeLogRequest(
                formOpenedFor, DAY, LocalTime.parse("08:00"), LocalTime.parse("17:00"), false, 60, "work")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("active placement has changed");

        // The draft landed nowhere: not on the old placement, and not silently on the new one.
        assertThat(logs.findByPlacementIdOrderByLogDateDesc(formOpenedFor)).isEmpty();
        assertThat(logs.findByPlacementIdOrderByLogDateDesc(newPlacement.getId())).isEmpty();
    }

    // ---- helpers ----

    /** Terminates the current placement and activates a new one, as an approved transfer does. */
    private Placement switchToNewPlacement(int requiredHours) {
        placement.terminate();
        placements.saveAndFlush(placement);
        JobApplication app = BeanUtils.instantiateClass(JobApplication.class);
        app.setStudentId(student.getId());
        app.setCompanyName("Beta Corp");
        app.setStatus(ApplicationStatus.ACCEPTED);
        applications.saveAndFlush(app);
        return placements.saveAndFlush(Placement.activate(app.getId(), student.getId(),
                LocalDate.of(2027, 1, 4), LocalDate.of(2027, 4, 30), requiredHours));
    }

    private TimeLog reload(Integer id) {
        em.flush();
        em.clear();
        return logs.findById(id).orElseThrow();
    }

    private TimeLog log(LocalDate date, String in, String out, boolean nextDay, int breakMinutes) {
        return TimeLog.submit(placement.getId(), student.getId(), date,
                LocalTime.parse(in), LocalTime.parse(out), nextDay, breakMinutes, "work");
    }

    private void insert(String in, String out, boolean nextDay, int breakMinutes,
                        String status, String feedback, String verifiedBy, String verifiedAt) {
        insertOn(DAY.toString(), in, out, nextDay, breakMinutes, status, feedback, verifiedBy, verifiedAt);
    }

    /** Raw INSERT with SQL literals (test-only constants), bypassing all Java checks. */
    private void insertOn(String date, String in, String out, boolean nextDay, int breakMinutes,
                          String status, String feedback, String verifiedBy, String verifiedAt) {
        em.createNativeQuery("INSERT INTO time_logs (placement_id, student_id, log_date, time_in, time_out, "
                + "ends_next_day, break_minutes, accomplishments, status, feedback, verified_by, verified_at) VALUES ("
                + placement.getId() + ", " + student.getId() + ", DATE '" + date + "', TIME '" + in + "', TIME '" + out
                + "', " + nextDay + ", " + breakMinutes + ", 'work', " + status + ", " + feedback + ", "
                + verifiedBy + ", " + verifiedAt + ")").executeUpdate();
    }

    private static void assertRejectedBy(String constraintName, Runnable action) {
        assertThatThrownBy(action::run).hasStackTraceContaining(constraintName);
    }
}
