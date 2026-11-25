package com.ojttrack.backend.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the database itself enforces the placement rules (V4 constraints and indexes),
 * so they hold even if Java code has a bug or two requests race.
 *
 * Needs the real PostgreSQL test database: see application-dbtest.properties.
 * Run from backend/:  .\mvnw.cmd test "-Dtest=*DbTest"   (with DB_PASSWORD set)
 * Flyway migrates ojttrack_test to the latest version; each test is rolled back afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // use Postgres, not an in-memory DB
@ActiveProfiles("dbtest")
@Import(PlacementService.class)
class PlacementDbTest {

    static final LocalDate START = LocalDate.of(2027, 1, 4);
    static final LocalDate END = LocalDate.of(2027, 4, 30);

    @Autowired AppUserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired PlacementRepository placements;
    @Autowired PlacementRequestRepository requests;
    @Autowired PlacementService service;

    AppUser coordinator;
    AppUser student;
    AppUser otherStudent;

    @BeforeEach
    void setUp() {
        coordinator = users.save(AppUser.coordinator("dbtest-coord@test.local", "hash", "DB Coordinator"));
        student = users.save(AppUser.student("dbtest-s1@test.local", "hash", "DB Student", coordinator.getId()));
        otherStudent = users.save(AppUser.student("dbtest-s2@test.local", "hash", "Other Student", coordinator.getId()));
    }

    // ---- constraints and indexes ----

    @Test
    void studentCannotHaveTwoActivePlacements() {
        JobApplication first = acceptedApplication(student, "First Co");
        JobApplication second = acceptedApplication(student, "Second Co");
        placements.saveAndFlush(Placement.activate(first.getId(), student.getId(), START, END, 486));

        assertThatThrownBy(() -> placements.saveAndFlush(
                Placement.activate(second.getId(), student.getId(), START, END, 486)))
                .isInstanceOf(DataIntegrityViolationException.class);   // ux_placements_one_active_per_student
    }

    @Test
    void applicationCannotProduceTwoPlacements() {
        JobApplication app = acceptedApplication(student, "Acme");
        Placement first = placements.saveAndFlush(Placement.activate(app.getId(), student.getId(), START, END, 486));
        first.terminate();
        placements.saveAndFlush(first);

        assertThatThrownBy(() -> placements.saveAndFlush(
                Placement.activate(app.getId(), student.getId(), START, END, 486)))
                .isInstanceOf(DataIntegrityViolationException.class);   // UNIQUE (application_id)
    }

    @Test
    void placementStudentMustMatchApplicationStudent() {
        JobApplication app = acceptedApplication(student, "Acme");

        assertThatThrownBy(() -> placements.saveAndFlush(
                Placement.activate(app.getId(), otherStudent.getId(), START, END, 486)))
                .isInstanceOf(DataIntegrityViolationException.class);   // composite FK (application_id, student_id)
    }

    @Test
    void endDateCannotBeBeforeStartDate() {
        JobApplication app = acceptedApplication(student, "Acme");

        assertThatThrownBy(() -> placements.saveAndFlush(
                Placement.activate(app.getId(), student.getId(), END, START, 486)))
                .isInstanceOf(DataIntegrityViolationException.class);   // chk_placements_dates
    }

    @Test
    void studentCannotHaveTwoPendingRequests() {
        JobApplication first = acceptedApplication(student, "First Co");
        JobApplication second = acceptedApplication(student, "Second Co");
        requests.saveAndFlush(PlacementRequest.submit(student.getId(), first.getId(), null, null));

        assertThatThrownBy(() -> requests.saveAndFlush(
                PlacementRequest.submit(student.getId(), second.getId(), null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);   // ux_placement_requests_one_pending_per_student
    }

    // ---- the full workflow through the service ----

    @Test
    void approvingAChangeTerminatesOldPlacementAndActivatesNewOne() {
        JobApplication first = acceptedApplication(student, "First Co");
        JobApplication second = acceptedApplication(student, "Second Co");

        var activation = service.submit(student.getId(), first.getId(), null);
        service.approve(coordinator.getId(), activation.id(), START, END, 486, null);

        var change = service.submit(student.getId(), second.getId(), "better fit");
        assertThat(change.type()).isEqualTo("CHANGE");
        // Rule 8: while the change is pending, the first placement is still ACTIVE.
        assertThat(placements.findByStudentIdAndStatus(student.getId(), PlacementStatus.ACTIVE))
                .get().extracting(Placement::getApplicationId).isEqualTo(first.getId());

        service.approve(coordinator.getId(), change.id(), START, END, 486, null);
        placements.flush();

        List<Placement> history = placements.findByStudentIdOrderByCreatedAtDesc(student.getId());
        assertThat(history).hasSize(2);
        assertThat(history).filteredOn(p -> p.getStatus() == PlacementStatus.ACTIVE)
                .singleElement().extracting(Placement::getApplicationId).isEqualTo(second.getId());
        assertThat(history).filteredOn(p -> p.getStatus() == PlacementStatus.TERMINATED)
                .singleElement().satisfies(p -> {
                    assertThat(p.getApplicationId()).isEqualTo(first.getId());
                    assertThat(p.getEndedAt()).isNotNull();
                });
    }

    @Test
    void rejectingAChangeKeepsCurrentPlacementActive() {
        JobApplication first = acceptedApplication(student, "First Co");
        JobApplication second = acceptedApplication(student, "Second Co");
        var activation = service.submit(student.getId(), first.getId(), null);
        service.approve(coordinator.getId(), activation.id(), START, END, 486, null);

        var change = service.submit(student.getId(), second.getId(), null);
        service.reject(coordinator.getId(), change.id(), "stay");
        placements.flush();

        assertThat(placements.findByStudentIdOrderByCreatedAtDesc(student.getId()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.getApplicationId()).isEqualTo(first.getId());
                    assertThat(p.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
                });
    }

    // ---- helpers ----

    private JobApplication acceptedApplication(AppUser owner, String company) {
        // JobApplication's no-arg constructor is protected (for JPA); BeanUtils can still call it.
        JobApplication app = BeanUtils.instantiateClass(JobApplication.class);
        app.setStudentId(owner.getId());
        app.setCompanyName(company);
        app.setStatus(ApplicationStatus.ACCEPTED);
        return applications.saveAndFlush(app);
    }
}
