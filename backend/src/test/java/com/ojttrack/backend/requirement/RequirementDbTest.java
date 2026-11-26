package com.ojttrack.backend.requirement;

import static com.ojttrack.backend.requirement.RequirementServiceTest.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ojttrack.backend.config.TimeConfig;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * What only PostgreSQL can prove: the V7 constraints, the cascade/RESTRICT pair that
 * protects history, and the real "active" JPQL queries (the unit tests mock them).
 * Constraint tests use raw SQL on purpose, to show the database rejects bad rows even
 * if the Java checks were bypassed.
 *
 * Needs ojttrack_test (see application-dbtest.properties). From backend/:
 *   .\mvnw.cmd test "-Dtest=*DbTest"   (with DB_PASSWORD set)
 * Each test is rolled back. A rejected statement aborts the transaction, so it is
 * always the last statement in its test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dbtest")
@Import({RequirementService.class, RequirementReviewService.class, StudentRequirementService.class, TimeConfig.class})
class RequirementDbTest {

    @Autowired AppUserRepository users;
    @Autowired RequirementRepository requirements;
    @Autowired RequirementAssignmentRepository assignments;
    @Autowired RequirementEventRepository events;
    @Autowired RequirementService management;
    @Autowired RequirementReviewService review;
    @Autowired StudentRequirementService studentService;
    @Autowired EntityManager em;

    AppUser coordinator;
    AppUser otherCoordinator;
    AppUser student;
    AppUser classmate;

    @BeforeEach
    void setUp() {
        coordinator = users.save(AppUser.coordinator("req-coord@test.local", "hash", "Req Coordinator"));
        otherCoordinator = users.save(AppUser.coordinator("req-coord2@test.local", "hash", "Req Other Coordinator"));
        student = users.save(AppUser.student("req-s1@test.local", "hash", "Req Student", coordinator.getId()));
        classmate = users.save(AppUser.student("req-s2@test.local", "hash", "Req Classmate", coordinator.getId()));
    }

    // ---- constraints (raw SQL) ----

    @Test
    void aStudentIsAssignedARequirementOnlyOnce() {
        Requirement moa = moa();
        insertAssignment(moa, student, "'NOT_STARTED'", "NULL", "NULL", "NULL", "NULL");

        assertThatThrownBy(() -> insertAssignment(moa, student, "'NOT_STARTED'", "NULL", "NULL", "NULL", "NULL"))
                .hasStackTraceContaining("ux_assignment_once");
    }

    @Test
    void notStartedCannotHaveASubmissionTime() {
        assertThatThrownBy(() -> insertAssignment(moa(), student, "'NOT_STARTED'", "now()", "NULL", "NULL", "NULL"))
                .hasStackTraceContaining("chk_assignment_submitted_at");
    }

    @Test
    void submittedNeedsASubmissionTime() {
        assertThatThrownBy(() -> insertAssignment(moa(), student, "'SUBMITTED'", "NULL", "NULL", "NULL", "NULL"))
                .hasStackTraceContaining("chk_assignment_submitted_at");
    }

    @Test
    void confirmationFieldsAreBothOrNeither() {
        assertThatThrownBy(() -> insertAssignment(moa(), student, "'CONFIRMED'", "now()",
                String.valueOf(coordinator.getId()), "NULL", "NULL"))
                .hasStackTraceContaining("chk_assignment_confirmed");
    }

    @Test
    void returnedNeedsRealFeedback() {
        assertThatThrownBy(() -> insertAssignment(moa(), student, "'RETURNED'", "now()", "NULL", "NULL", "'   '"))
                .hasStackTraceContaining("chk_assignment_returned_feedback");
    }

    @Test
    void returnAndUnconfirmEventsNeedAReason() {
        Integer assignmentId = insertAssignment(moa(), student, "'NOT_STARTED'", "NULL", "NULL", "NULL", "NULL");

        assertThatThrownBy(() -> em.createNativeQuery(
                "INSERT INTO requirement_events (assignment_id, action, actor_id, text) VALUES ("
                        + assignmentId + ", 'UNCONFIRMED', " + coordinator.getId() + ", NULL)").executeUpdate())
                .hasStackTraceContaining("chk_event_reason");
    }

    // ---- deletion keeps history ----

    @Test
    void deletingARequirementWithoutHistoryCascadesToItsAssignments() {
        RequirementResponse created = management.create(coordinator.getId(),
                new CreateRequirementRequest("Submit signed MOA", null, null, null));   // all current students
        flushAndClear();
        assertThat(assignments.findByRequirementId(created.id())).hasSize(2);

        management.delete(coordinator.getId(), created.id(), created.version());
        flushAndClear();

        assertThat(requirements.findById(created.id())).isEmpty();
        assertThat(assignments.findByRequirementId(created.id())).isEmpty();   // ON DELETE CASCADE
    }

    @Test
    void postgresItselfRefusesToDeleteARequirementWithHistory() {
        RequirementResponse created = management.create(coordinator.getId(),
                new CreateRequirementRequest("Submit signed MOA", null, null, List.of(student.getId())));
        RequirementAssignment mine = assignments.findByRequirementIdAndStudentId(created.id(), student.getId()).orElseThrow();
        studentService.submit(student.getId(), mine.getId(), mine.getVersion(), "Left it at the office");
        flushAndClear();

        // Bypass the service check: the RESTRICT FK on requirement_events stops the cascade.
        assertThatThrownBy(() -> em.createNativeQuery("DELETE FROM requirements WHERE id = " + created.id()).executeUpdate())
                .hasStackTraceContaining("requirement_events_assignment_id_fkey");
    }

    // ---- coordinator reassignment ----

    @Test
    void aStudentWithAssignmentsCanMoveToAnotherCoordinatorAndTheOwnerKeepsAuthority() {
        RequirementResponse created = management.create(coordinator.getId(),
                new CreateRequirementRequest("Submit signed MOA", null, null, List.of(student.getId())));
        RequirementAssignment mine = assignments.findByRequirementIdAndStudentId(created.id(), student.getId()).orElseThrow();

        int moved = em.createNativeQuery("UPDATE users SET coordinator_id = " + otherCoordinator.getId()
                + " WHERE id = " + student.getId()).executeUpdate();
        flushAndClear();
        assertThat(moved).isEqualTo(1);   // the schema doesn't block a future reassignment feature

        StudentRequirementResponse submitted = studentService.submit(student.getId(), mine.getId(), mine.getVersion(), null);
        AssignmentResponse confirmed = review.confirm(coordinator.getId(), mine.getId(), submitted.version());
        assertThat(confirmed.status()).isEqualTo(AssignmentStatus.CONFIRMED);   // the owner keeps authority

        // Last: a thrown service exception marks the test transaction rollback-only.
        assertStatus(() -> review.unconfirm(otherCoordinator.getId(), mine.getId(), confirmed.version(), "x"),
                HttpStatus.NOT_FOUND);
    }

    // ---- lifecycle and history ----

    @Test
    void lifecycleWritesTheHistoryAndEachEventKeepsItsOwnText() {
        RequirementResponse created = management.create(coordinator.getId(),
                new CreateRequirementRequest("Submit signed MOA", null, null, List.of(student.getId())));
        Integer id = assignments.findByRequirementIdAndStudentId(created.id(), student.getId()).orElseThrow().getId();

        int v = studentService.submit(student.getId(), id, 0, "First copy").version();
        v = review.returnWithFeedback(coordinator.getId(), id, v, "Needs the school's stamp").version();
        v = studentService.submit(student.getId(), id, v, "Stamped copy").version();
        v = review.confirm(coordinator.getId(), id, v).version();
        review.unconfirm(coordinator.getId(), id, v, "Confirmed the wrong student");
        flushAndClear();

        RequirementAssignment current = assignments.findById(id).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(AssignmentStatus.RETURNED);
        assertThat(current.getNote()).isEqualTo("Stamped copy");                     // only the LATEST note
        assertThat(current.getConfirmedBy()).isNull();
        assertThat(events.findByAssignmentIdInOrderByCreatedAtAscIdAsc(List.of(id)))
                .extracting(RequirementEvent::getAction, RequirementEvent::getText)
                .containsExactly(
                        tuple(EventAction.SUBMITTED, "First copy"),   // kept after resubmit
                        tuple(EventAction.RETURNED, "Needs the school's stamp"),
                        tuple(EventAction.SUBMITTED, "Stamped copy"),
                        tuple(EventAction.CONFIRMED, null),
                        tuple(EventAction.UNCONFIRMED, "Confirmed the wrong student"));
        assertThat(events.existsByRequirementId(created.id())).isTrue();
    }

    // ---- the "active" queries, against real SQL ----

    @Test
    void activeQueriesSkipArchivedRequirementsAndOtherCoordinatorsRequirements() {
        Requirement active = moa();
        Requirement archived = requirements.saveAndFlush(
                Requirement.create(coordinator.getId(), "Old waiver", null, null));
        Requirement someoneElses = requirements.saveAndFlush(
                Requirement.create(otherCoordinator.getId(), "Other school form", null, null));
        Integer activeId = insertAssignment(active, student, "'SUBMITTED'", "now()", "NULL", "NULL", "NULL");
        insertAssignment(archived, student, "'SUBMITTED'", "now()", "NULL", "NULL", "NULL");
        insertAssignment(someoneElses, student, "'SUBMITTED'", "now()", "NULL", "NULL", "NULL");
        archived.archive(Instant.now());
        requirements.saveAndFlush(archived);
        flushAndClear();

        assertThat(assignments.findActiveByStudentId(student.getId())).hasSize(2);   // any owner, not archived
        assertThat(assignments.findByStudentId(student.getId())).hasSize(3);         // "Show archived": all of them
        assertThat(assignments.findActiveForCoordinator(coordinator.getId(), List.of(student.getId())))
                .extracting(RequirementAssignment::getId).containsExactly(activeId);
        assertThat(assignments.countActiveSubmittedForCoordinator(coordinator.getId())).isEqualTo(1);
        assertThat(assignments.countActiveSubmittedForCoordinator(otherCoordinator.getId())).isEqualTo(1);
        assertThat(events.existsByRequirementId(active.getId())).isFalse();
    }

    // ---- helpers ----

    private Requirement moa() {
        return requirements.saveAndFlush(Requirement.create(coordinator.getId(), "Submit signed MOA", null, null));
    }

    /** Raw insert; the SQL fragments are trusted test literals. Returns the new id. */
    private Integer insertAssignment(Requirement r, AppUser s, String status, String submittedAt,
                                     String confirmedBy, String confirmedAt, String feedback) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO requirement_assignments "
                        + "(requirement_id, student_id, status, submitted_at, confirmed_by, confirmed_at, feedback) "
                        + "VALUES (" + r.getId() + ", " + s.getId() + ", " + status + ", " + submittedAt + ", "
                        + confirmedBy + ", " + confirmedAt + ", " + feedback + ") RETURNING id")
                .getSingleResult()).intValue();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
