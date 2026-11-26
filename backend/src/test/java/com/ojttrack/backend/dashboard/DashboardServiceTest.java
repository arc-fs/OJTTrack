package com.ojttrack.backend.dashboard;

import static com.ojttrack.backend.dashboard.AssistanceRulesTest.log;
import static com.ojttrack.backend.dashboard.AssistanceRulesTest.placement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.dashboard.AssistanceFlag.Code;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementRequest;
import com.ojttrack.backend.placement.PlacementRequestRepository;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.requirement.AssignmentStatus;
import com.ojttrack.backend.requirement.Requirement;
import com.ojttrack.backend.requirement.RequirementAssignmentRepository;
import com.ojttrack.backend.requirement.RequirementFixtures;
import com.ojttrack.backend.requirement.RequirementRepository;
import com.ojttrack.backend.timelog.TimeLogRepository;
import com.ojttrack.backend.timelog.TimeLogStatus;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Dashboard assembly: selection, filtering, totals and ordering. Repositories are mocks. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    // 2027-01-20 in Manila
    static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-20T02:00:00Z"), ZoneId.of("Asia/Manila"));
    static final LocalDate TODAY = LocalDate.of(2027, 1, 20);
    static final int STUDENT = 10;

    @Mock JobApplicationRepository applications;
    @Mock PlacementRepository placements;
    @Mock PlacementRequestRepository requests;
    @Mock TimeLogRepository logs;
    @Mock AppUserRepository users;
    @Mock RequirementRepository requirementRepo;
    @Mock RequirementAssignmentRepository requirementAssignments;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(applications, placements, requests, logs, users,
                requirementRepo, requirementAssignments, CLOCK);
    }

    // ---- student dashboard ----

    @Test
    void countsApplicationsByStatusIncludingZeros() {
        when(applications.findByStudentId(STUDENT)).thenReturn(List.of(
                app(1, "A", ApplicationStatus.APPLIED, null), app(2, "B", ApplicationStatus.APPLIED, null),
                app(3, "C", ApplicationStatus.REJECTED, null)));

        StudentDashboardResponse dashboard = service.studentDashboard(STUDENT);

        assertThat(dashboard.applicationCounts().get(ApplicationStatus.APPLIED)).isEqualTo(2);
        assertThat(dashboard.applicationCounts().get(ApplicationStatus.REJECTED)).isEqualTo(1);
        assertThat(dashboard.applicationCounts().get(ApplicationStatus.OFFERED)).isZero();
        assertThat(dashboard.placement()).isNull();
        assertThat(dashboard.pendingPlacementRequest()).isNull();
    }

    @Test
    void followUpsAreOpenOnesOverdueOrWithinSevenDaysOverdueFirst() {
        when(applications.findByStudentId(STUDENT)).thenReturn(List.of(
                app(1, "Due in 7 days", ApplicationStatus.APPLIED, TODAY.plusDays(7)),
                app(2, "Due in 8 days", ApplicationStatus.APPLIED, TODAY.plusDays(8)),          // too far
                app(3, "Overdue", ApplicationStatus.INTERVIEW, TODAY.minusDays(2)),
                app(4, "Settled", ApplicationStatus.REJECTED, TODAY.minusDays(1)),               // not relevant
                app(5, "Today", ApplicationStatus.PLANNED, TODAY)));

        List<StudentDashboardResponse.FollowUp> followUps = service.studentDashboard(STUDENT).followUps();

        assertThat(followUps).extracting(StudentDashboardResponse.FollowUp::companyName)
                .containsExactly("Overdue", "Today", "Due in 7 days");
        assertThat(followUps).extracting(StudentDashboardResponse.FollowUp::overdue)
                .containsExactly(true, false, false);
    }

    @Test
    void showsTheActivePlacementWithProgress() {
        when(applications.findByStudentId(STUDENT)).thenReturn(List.of(app(101, "Acme", ApplicationStatus.ACCEPTED, null)));
        Placement active = placement(1, PlacementStatus.ACTIVE, TODAY.minusDays(10), TODAY.plusDays(60));
        when(placements.findByStudentIdOrderByCreatedAtDesc(STUDENT)).thenReturn(List.of(active));
        when(logs.findByPlacementIdOrderByLogDateDesc(1)).thenReturn(List.of(log(1, TODAY.minusDays(1), TimeLogStatus.VERIFIED, 480)));

        DashboardPlacement shown = service.studentDashboard(STUDENT).placement();

        assertThat(shown.companyName()).isEqualTo("Acme");
        assertThat(shown.role()).isEqualTo("Intern");   // the placement's role reaches the dashboard
        assertThat(shown.progress().verifiedMinutes()).isEqualTo(480);
    }

    @Test
    void withoutAnActivePlacementShowsTheMostRecentlyCompletedOne() {
        Placement older = placement(1, PlacementStatus.COMPLETED, TODAY.minusDays(200), TODAY.minusDays(100));
        ReflectionTestUtils.setField(older, "endedAt", Instant.parse("2026-06-01T00:00:00Z"));
        Placement newer = placement(2, PlacementStatus.COMPLETED, TODAY.minusDays(90), TODAY.minusDays(10));
        ReflectionTestUtils.setField(newer, "endedAt", Instant.parse("2026-12-01T00:00:00Z"));
        when(placements.findByStudentIdOrderByCreatedAtDesc(STUDENT)).thenReturn(List.of(older, newer));
        when(logs.findByPlacementIdOrderByLogDateDesc(any())).thenReturn(List.of());

        assertThat(service.studentDashboard(STUDENT).placement().placementId()).isEqualTo(2);
    }

    @Test
    void showsAPendingPlacementRequest() {
        when(applications.findByStudentId(STUDENT)).thenReturn(List.of(app(101, "Acme", ApplicationStatus.ACCEPTED, null)));
        when(requests.findByStudentIdOrderByCreatedAtDesc(STUDENT))
                .thenReturn(List.of(PlacementRequest.submit(STUDENT, 101, null, null)));

        StudentDashboardResponse.PendingRequest pending = service.studentDashboard(STUDENT).pendingPlacementRequest();

        assertThat(pending.companyName()).isEqualTo("Acme");
        assertThat(pending.type()).isEqualTo("ACTIVATION");
    }

    // ---- coordinator dashboard ----

    @Test
    void totalsCountPlacementsAndOnlyReviewableLogs() {
        AppUser ana = student(10, "Ana");
        AppUser ben = student(11, "Ben");
        when(users.findByCoordinatorIdOrderByFullNameAsc(1)).thenReturn(List.of(ana, ben));

        Placement anaOld = placementOf(1, PlacementStatus.TERMINATED, TODAY.minusDays(60), TODAY.plusDays(30), 10);
        Placement anaActive = placementOf(2, PlacementStatus.ACTIVE, TODAY.minusDays(5), TODAY.plusDays(60), 10);
        Placement benDone = placementOf(3, PlacementStatus.COMPLETED, TODAY.minusDays(120), TODAY.minusDays(5), 11);
        when(placements.findByStudentIdIn(anyCollection())).thenReturn(List.of(anaOld, anaActive, benDone));
        // Logs for the shown placements: Ana's active one (2) and Ben's completed one (3).
        when(logs.findByPlacementIdIn(anyCollection())).thenReturn(List.of(
                log(2, TODAY.minusDays(1), TimeLogStatus.PENDING, 480),
                log(2, TODAY.minusDays(2), TimeLogStatus.PENDING, 480),
                log(3, TODAY.minusDays(30), TimeLogStatus.PENDING, 480)));   // frozen on a completed placement
        when(applications.findAllById(anyCollection())).thenReturn(List.of());

        CoordinatorDashboardResponse.Totals totals = service.coordinatorDashboard(1).totals();

        assertThat(totals.students()).isEqualTo(2);
        assertThat(totals.activePlacements()).isEqualTo(1);
        assertThat(totals.completedPlacements()).isEqualTo(1);
        // Only the 2 PENDING logs on the ACTIVE placement: same as the Review queue.
        assertThat(totals.logsAwaitingReview()).isEqualTo(2);
    }

    @Test
    void flaggedStudentsComeFirstWithAllReasons() {
        AppUser ana = student(10, "Ana");   // no flags
        AppUser ben = student(11, "Ben");   // NO_PLACEMENT
        ReflectionTestUtils.setField(ben, "placementSearchStartsOn", TODAY.minusDays(3));
        when(users.findByCoordinatorIdOrderByFullNameAsc(1)).thenReturn(List.of(ana, ben));
        when(placements.findByStudentIdIn(anyCollection())).thenReturn(List.of());

        List<CoordinatorDashboardResponse.StudentRow> rows = service.coordinatorDashboard(1).students();

        assertThat(rows).extracting(CoordinatorDashboardResponse.StudentRow::fullName).containsExactly("Ben", "Ana");
        assertThat(rows.get(0).flags()).extracting(AssistanceFlag::code).containsExactly(Code.NO_PLACEMENT);
        assertThat(rows.get(1).flags()).isEmpty();
    }

    @Test
    void coordinatorWithoutStudentsGetsAnEmptyDashboard() {
        when(users.findByCoordinatorIdOrderByFullNameAsc(1)).thenReturn(List.of());

        CoordinatorDashboardResponse dashboard = service.coordinatorDashboard(1);

        assertThat(dashboard.students()).isEmpty();
        assertThat(dashboard.totals().students()).isZero();
    }

    // ---- requirements (§4.7) ----

    @Test
    void studentDashboardListsOverdueDueSoonAndReturnedRequirements() {
        Requirement overdue = RequirementFixtures.requirement(50, 1, "Submit signed MOA", TODAY.minusDays(2));
        Requirement soon = RequirementFixtures.requirement(51, 1, "Weekly report", TODAY.plusDays(3));
        Requirement waiting = RequirementFixtures.requirement(52, 1, "Insurance form", TODAY.minusDays(5));
        when(requirementAssignments.findActiveByStudentId(STUDENT)).thenReturn(List.of(
                RequirementFixtures.assignment(70, overdue, STUDENT, AssignmentStatus.RETURNED),
                RequirementFixtures.assignment(71, soon, STUDENT, AssignmentStatus.NOT_STARTED),
                RequirementFixtures.assignment(72, waiting, STUDENT, AssignmentStatus.SUBMITTED)));   // never overdue
        when(requirementRepo.findAllById(anyCollection())).thenReturn(List.of(overdue, soon, waiting));

        StudentDashboardResponse.Requirements section = service.studentDashboard(STUDENT).requirements();

        assertThat(section.overdue()).extracting(StudentDashboardResponse.RequirementItem::title)
                .containsExactly("Submit signed MOA");
        assertThat(section.dueSoon()).extracting(StudentDashboardResponse.RequirementItem::title)
                .containsExactly("Weekly report");
        assertThat(section.returned()).extracting(StudentDashboardResponse.RequirementItem::feedback)
                .containsExactly("Please fix");
    }

    @Test
    void coordinatorRowsListOverdueRequirementsAndTheFifthFlag() {
        AppUser ana = student(10, "Ana");
        when(users.findByCoordinatorIdOrderByFullNameAsc(1)).thenReturn(List.of(ana));
        when(placements.findByStudentIdIn(anyCollection())).thenReturn(List.of());
        Requirement moa = RequirementFixtures.requirement(50, 1, "Submit signed MOA", TODAY.minusDays(2));
        when(requirementRepo.findByCoordinatorIdAndArchivedAtIsNull(1)).thenReturn(List.of(moa));
        when(requirementAssignments.findActiveForCoordinator(any(), anyCollection())).thenReturn(List.of(
                RequirementFixtures.assignment(70, moa, 10, AssignmentStatus.NOT_STARTED)));
        when(requirementAssignments.countActiveSubmittedForCoordinator(1)).thenReturn(3L);

        CoordinatorDashboardResponse dashboard = service.coordinatorDashboard(1);

        CoordinatorDashboardResponse.StudentRow row = dashboard.students().get(0);
        assertThat(row.flags()).extracting(AssistanceFlag::code).containsExactly(Code.OVERDUE_REQUIREMENTS);
        assertThat(row.overdueRequirements()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Submit signed MOA");
            assertThat(item.dueDate()).isEqualTo(TODAY.minusDays(2));
            assertThat(item.status()).isEqualTo(AssignmentStatus.NOT_STARTED);
        });
        assertThat(dashboard.totals().requirementsAwaitingReview()).isEqualTo(3);
    }

    @Test
    void awaitingReviewCountsEvenWithoutCurrentStudents() {
        // e.g. a student was reassigned away but still has a submission on this coordinator's requirement
        when(users.findByCoordinatorIdOrderByFullNameAsc(1)).thenReturn(List.of());
        when(requirementAssignments.countActiveSubmittedForCoordinator(1)).thenReturn(1L);

        assertThat(service.coordinatorDashboard(1).totals().requirementsAwaitingReview()).isEqualTo(1);
    }

    // ---- helpers ----

    private static JobApplication app(int id, String company, ApplicationStatus status, LocalDate followUp) {
        JobApplication app = BeanUtils.instantiateClass(JobApplication.class);
        ReflectionTestUtils.setField(app, "id", id);
        app.setStudentId(STUDENT);
        app.setCompanyName(company);
        app.setStatus(status);
        app.setFollowUpDate(followUp);
        return app;
    }

    private static AppUser student(int id, String name) {
        AppUser user = TestUsers.studentEntity(id, 1);
        ReflectionTestUtils.setField(user, "fullName", name);
        return user;
    }

    private static Placement placementOf(int id, PlacementStatus status, LocalDate start, LocalDate end, int studentId) {
        Placement placement = AssistanceRulesTest.placement(id, status, start, end);
        ReflectionTestUtils.setField(placement, "studentId", studentId);
        return placement;
    }
}
