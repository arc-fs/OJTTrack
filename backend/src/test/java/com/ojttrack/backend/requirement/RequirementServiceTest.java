package com.ojttrack.backend.requirement;

import static com.ojttrack.backend.requirement.RequirementFixtures.archived;
import static com.ojttrack.backend.requirement.RequirementFixtures.assignment;
import static com.ojttrack.backend.requirement.RequirementFixtures.requirement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

/** Coordinator management: ownership, archived = read-only, versions, delete/remove rules. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequirementServiceTest {

    static final int OWNER = 1;
    static final int OTHER_COORDINATOR = 2;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-20T02:00:00Z"), ZoneId.of("Asia/Manila"));

    @Mock RequirementRepository requirements;
    @Mock RequirementAssignmentRepository assignments;
    @Mock RequirementEventRepository events;
    @Mock AppUserRepository users;

    RequirementService service;
    Requirement moa;

    @BeforeEach
    void setUp() {
        service = new RequirementService(requirements, assignments, events, users, CLOCK);
        moa = requirement(50, OWNER, "Submit signed MOA", LocalDate.of(2027, 1, 31));
        when(requirements.findById(50)).thenReturn(Optional.of(moa));
        when(requirements.saveAndFlush(any(Requirement.class))).thenAnswer(call -> {
            Requirement r = call.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 51);
            return r;
        });
        when(assignments.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
    }

    // ---- create ----

    @Test
    void createWithoutStudentIdsAssignsAllCurrentStudents() {
        when(users.findByCoordinatorIdOrderByFullNameAsc(OWNER))
                .thenReturn(List.of(TestUsers.studentEntity(10, OWNER), TestUsers.studentEntity(11, OWNER)));

        RequirementResponse created = service.create(OWNER, new CreateRequirementRequest("  Weekly report #1 ", null, null, null));

        assertThat(created.title()).isEqualTo("Weekly report #1");
        assertThat(created.progressCounts().notStarted()).isEqualTo(2);
        ArgumentCaptor<List<RequirementAssignment>> saved = assignmentsCaptor();
        verify(assignments).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(RequirementAssignment::getStudentId).containsExactly(10, 11);
        assertThat(saved.getValue()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(AssignmentStatus.NOT_STARTED));
    }

    @Test
    void createForSpecificStudentsAssignsOnlyThem() {
        when(users.findAllById(anyCollection())).thenReturn(List.of(TestUsers.studentEntity(11, OWNER)));

        service.create(OWNER, new CreateRequirementRequest("Weekly report #1", null, null, List.of(11)));

        ArgumentCaptor<List<RequirementAssignment>> saved = assignmentsCaptor();
        verify(assignments).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(RequirementAssignment::getStudentId).containsExactly(11);
        verify(users, never()).findByCoordinatorIdOrderByFullNameAsc(any());
    }

    @Test
    void createRejectsAnotherCoordinatorsStudent() {
        when(users.findAllById(anyCollection()))
                .thenReturn(List.of(TestUsers.studentEntity(10, OWNER), TestUsers.studentEntity(99, OTHER_COORDINATOR)));

        assertStatus(() -> service.create(OWNER, new CreateRequirementRequest("MOA", null, null, List.of(10, 99))),
                HttpStatus.BAD_REQUEST);
        verify(requirements, never()).saveAndFlush(any());
    }

    // ---- ownership: one rule for every operation ----

    @Test
    void everyOperationOnAnotherCoordinatorsRequirementIsNotFound() {
        assertStatus(() -> service.update(OTHER_COORDINATOR, 50, new UpdateRequirementRequest(0, "x", null, null)), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.assign(OTHER_COORDINATOR, 50, new AssignStudentsRequest(List.of(10))), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.removeStudent(OTHER_COORDINATOR, 50, 10), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.archive(OTHER_COORDINATOR, 50, 0), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.delete(OTHER_COORDINATOR, 50, 0), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.assignments(OTHER_COORDINATOR, 50), HttpStatus.NOT_FOUND);
    }

    // ---- versions ----

    @Test
    void staleVersionIsAConflictForEditArchiveAndDelete() {
        ReflectionTestUtils.setField(moa, "version", 3);
        assertMessage(() -> service.update(OWNER, 50, new UpdateRequirementRequest(2, "MOA", null, null)), "changed since you loaded it");
        assertMessage(() -> service.archive(OWNER, 50, 2), "changed since you loaded it");
        assertMessage(() -> service.delete(OWNER, 50, 2), "changed since you loaded it");
        verify(requirements, never()).delete(any());
    }

    @Test
    void editChangesTitleDescriptionAndDueDate() {
        when(assignments.findByRequirementId(50)).thenReturn(List.of());

        RequirementResponse edited = service.update(OWNER, 50,
                new UpdateRequirementRequest(0, "Submit signed MOA (2 copies)", "Bring originals", LocalDate.of(2027, 2, 5)));

        assertThat(edited.title()).isEqualTo("Submit signed MOA (2 copies)");
        assertThat(edited.dueDate()).isEqualTo(LocalDate.of(2027, 2, 5));
    }

    // ---- archived = read-only ----

    @Test
    void archivedRequirementsCantBeEditedOrReassigned() {
        archived(moa);
        assertMessage(() -> service.update(OWNER, 50, new UpdateRequirementRequest(0, "MOA", null, null)), "archived");
        assertMessage(() -> service.assign(OWNER, 50, new AssignStudentsRequest(List.of(10))), "archived");
        assertMessage(() -> service.removeStudent(OWNER, 50, 10), "archived");
        assertMessage(() -> service.archive(OWNER, 50, 0), "Already archived");
    }

    @Test
    void archivedRequirementsStayViewable() {
        archived(moa);
        when(assignments.findByRequirementId(50)).thenReturn(List.of(assignment(70, moa, 10, AssignmentStatus.CONFIRMED)));

        assertThat(service.assignments(OWNER, 50)).hasSize(1);
    }

    @Test
    void archivedWithHistoryCantBeDeletedWithoutHistoryCan() {
        archived(moa);
        when(events.existsByRequirementId(50)).thenReturn(true);
        assertMessage(() -> service.delete(OWNER, 50, 0), "Archive it instead");
        verify(requirements, never()).delete(any());

        when(events.existsByRequirementId(50)).thenReturn(false);
        service.delete(OWNER, 50, 0);
        verify(requirements).delete(moa);
    }

    // ---- delete and remove ----

    @Test
    void deleteWithHistoryIsRefusedWithoutHistoryIsAllowed() {
        when(events.existsByRequirementId(50)).thenReturn(true);
        assertMessage(() -> service.delete(OWNER, 50, 0), "Archive it instead");
        verify(requirements, never()).delete(any());

        when(events.existsByRequirementId(50)).thenReturn(false);
        service.delete(OWNER, 50, 0);
        verify(requirements).delete(moa);   // the database cascades to the untouched assignments
    }

    @Test
    void removingAStudentWhoAlreadySubmittedIsRefused() {
        RequirementAssignment submitted = assignment(70, moa, 10, AssignmentStatus.SUBMITTED);
        when(assignments.findByRequirementIdAndStudentId(50, 10)).thenReturn(Optional.of(submitted));
        when(events.existsByAssignmentId(70)).thenReturn(true);

        assertStatus(() -> service.removeStudent(OWNER, 50, 10), HttpStatus.CONFLICT);
        verify(assignments, never()).delete(any());
    }

    @Test
    void removingAStudentWithoutHistoryDeletesTheAssignment() {
        RequirementAssignment untouched = assignment(70, moa, 10, AssignmentStatus.NOT_STARTED);
        when(assignments.findByRequirementIdAndStudentId(50, 10)).thenReturn(Optional.of(untouched));

        service.removeStudent(OWNER, 50, 10);

        verify(assignments).delete(untouched);
    }

    // ---- assign more, counts, summary ----

    @Test
    void assignSkipsStudentsAlreadyAssigned() {
        when(users.findAllById(anyCollection()))
                .thenReturn(List.of(TestUsers.studentEntity(10, OWNER), TestUsers.studentEntity(12, OWNER)));
        when(assignments.findByRequirementId(50)).thenReturn(List.of(assignment(70, moa, 10, AssignmentStatus.NOT_STARTED)));

        service.assign(OWNER, 50, new AssignStudentsRequest(List.of(10, 12)));

        ArgumentCaptor<List<RequirementAssignment>> saved = assignmentsCaptor();
        verify(assignments).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(RequirementAssignment::getStudentId).containsExactly(12);
    }

    @Test
    void listHidesArchivedByDefaultAndCountsProgress() {
        Requirement old = archived(requirement(49, OWNER, "Old form", null));
        when(requirements.findByCoordinatorIdOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(moa, old));
        when(assignments.findByRequirementIdIn(anyCollection())).thenReturn(List.of(
                assignment(70, moa, 10, AssignmentStatus.SUBMITTED), assignment(71, moa, 11, AssignmentStatus.CONFIRMED),
                assignment(72, moa, 12, AssignmentStatus.NOT_STARTED)));

        List<RequirementResponse> active = service.list(OWNER, false);

        assertThat(active).extracting(RequirementResponse::id).containsExactly(50);
        assertThat(active.get(0).progressCounts())
                .isEqualTo(new RequirementResponse.ProgressCounts(1, 1, 0, 1));
        assertThat(service.list(OWNER, true)).extracting(RequirementResponse::id).containsExactly(50, 49);
    }

    @Test
    void completionSummaryIsForMyStudentsAndMyActiveRequirementsOnly() {
        when(users.findById(10)).thenReturn(Optional.of(TestUsers.studentEntity(10, OWNER)));
        Requirement overdue = requirement(52, OWNER, "Weekly report #1", LocalDate.of(2027, 1, 10));
        when(assignments.findActiveForCoordinator(OWNER, List.of(10))).thenReturn(List.of(
                assignment(70, moa, 10, AssignmentStatus.CONFIRMED),
                assignment(71, overdue, 10, AssignmentStatus.RETURNED)));
        when(requirements.findAllById(anyCollection())).thenReturn(List.of(moa, overdue));

        RequirementSummary summary = service.studentSummary(OWNER, 10);

        assertThat(summary.confirmed()).isEqualTo(1);
        assertThat(summary.returned()).isEqualTo(1);
        assertThat(summary.overdue()).extracting(RequirementSummary.OverdueItem::title).containsExactly("Weekly report #1");

        when(users.findById(20)).thenReturn(Optional.of(TestUsers.studentEntity(20, OTHER_COORDINATOR)));
        assertStatus(() -> service.studentSummary(OWNER, 20), HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<RequirementAssignment>> assignmentsCaptor() {
        return ArgumentCaptor.forClass((Class<List<RequirementAssignment>>) (Class<?>) List.class);
    }

    static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo((HttpStatusCode) expected);
    }

    static void assertMessage(Runnable action, String expectedText) {
        assertThatThrownBy(action::run).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getReason()).contains(expectedText));
    }
}
