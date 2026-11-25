package com.ojttrack.backend.timelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

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
class TimeLogDbTest {

    static final LocalDate DAY = LocalDate.of(2027, 1, 6);

    @Autowired AppUserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired PlacementRepository placements;
    @Autowired PlacementRequestRepository requests;
    @Autowired TimeLogRepository logs;
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

    // ---- helpers ----

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
