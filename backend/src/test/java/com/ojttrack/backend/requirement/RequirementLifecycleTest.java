package com.ojttrack.backend.requirement;

import static com.ojttrack.backend.requirement.RequirementFixtures.archived;
import static com.ojttrack.backend.requirement.RequirementFixtures.assignment;
import static com.ojttrack.backend.requirement.RequirementFixtures.requirement;
import static com.ojttrack.backend.requirement.RequirementServiceTest.assertMessage;
import static com.ojttrack.backend.requirement.RequirementServiceTest.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The submission lifecycle: student submits, the requirement's OWNER confirms / returns /
 * un-confirms. Every step writes a history event with its own copy of the note or reason.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequirementLifecycleTest {

    static final int OWNER = 1;
    static final int NEW_COORDINATOR = 2;
    static final int STUDENT = 10;
    static final Instant NOW = Instant.parse("2027-01-20T02:00:00Z");

    @Mock RequirementRepository requirements;
    @Mock RequirementAssignmentRepository assignments;
    @Mock RequirementEventRepository events;
    @Mock AppUserRepository users;

    StudentRequirementService student;
    RequirementReviewService review;
    Requirement moa;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Manila"));
        student = new StudentRequirementService(requirements, assignments, events, users, clock);
        review = new RequirementReviewService(requirements, assignments, events, users, clock);
        moa = requirement(50, OWNER, "Submit signed MOA", LocalDate.of(2027, 1, 15));
        when(requirements.findById(50)).thenReturn(Optional.of(moa));
        when(assignments.saveAndFlush(any(RequirementAssignment.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- student submits ----

    @Test
    void submitRecordsTheNoteAndAHistoryEvent() {
        RequirementAssignment a = given(AssignmentStatus.NOT_STARTED);

        StudentRequirementResponse response = student.submit(STUDENT, 70, 0, "  Left it at the office  ");

        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.SUBMITTED);
        assertThat(a.getNote()).isEqualTo("Left it at the office");
        assertThat(a.getSubmittedAt()).isEqualTo(NOW);
        assertThat(response.overdue()).isFalse();   // submitted: never overdue, even past the due date
        RequirementEvent event = savedEvent();
        assertThat(event.getAction()).isEqualTo(EventAction.SUBMITTED);
        assertThat(event.getActorId()).isEqualTo(STUDENT);
        assertThat(event.getText()).isEqualTo("Left it at the office");
    }

    @Test
    void returnedCanBeResubmittedSubmittedAndConfirmedCannot() {
        given(AssignmentStatus.RETURNED);
        student.submit(STUDENT, 70, 0, null);

        given(AssignmentStatus.SUBMITTED);
        assertMessage(() -> student.submit(STUDENT, 70, 0, null), "already submitted");
        given(AssignmentStatus.CONFIRMED);
        assertMessage(() -> student.submit(STUDENT, 70, 0, null), "already confirmed");
    }

    @Test
    void studentsCantSubmitSomeoneElsesStaleOrArchivedRequirement() {
        when(assignments.findByIdAndStudentId(70, STUDENT)).thenReturn(Optional.empty());
        assertStatus(() -> student.submit(STUDENT, 70, 0, null), HttpStatus.NOT_FOUND);

        RequirementAssignment a = given(AssignmentStatus.NOT_STARTED);
        ReflectionTestUtils.setField(a, "version", 4);
        assertMessage(() -> student.submit(STUDENT, 70, 3, null), "changed since you loaded it");

        ReflectionTestUtils.setField(a, "version", 0);
        archived(moa);
        assertMessage(() -> student.submit(STUDENT, 70, 0, null), "archived");
        verify(events, never()).save(any());
    }

    // ---- owner reviews ----

    @Test
    void ownerConfirmsAndHistoryRecordsIt() {
        RequirementAssignment a = given(AssignmentStatus.SUBMITTED);

        review.confirm(OWNER, 70, 0);

        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.CONFIRMED);
        assertThat(a.getConfirmedBy()).isEqualTo(OWNER);
        assertThat(a.getConfirmedAt()).isEqualTo(NOW);
        assertThat(savedEvent().getAction()).isEqualTo(EventAction.CONFIRMED);
    }

    @Test
    void returnKeepsTheTrimmedFeedbackOnTheAssignmentAndInHistory() {
        RequirementAssignment a = given(AssignmentStatus.SUBMITTED);

        review.returnWithFeedback(OWNER, 70, 0, "  Needs the school's stamp  ");

        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.RETURNED);
        assertThat(a.getFeedback()).isEqualTo("Needs the school's stamp");
        RequirementEvent event = savedEvent();
        assertThat(event.getAction()).isEqualTo(EventAction.RETURNED);
        assertThat(event.getText()).isEqualTo("Needs the school's stamp");
    }

    @Test
    void unconfirmReturnsWithTheReasonAndClearsConfirmation() {
        RequirementAssignment a = given(AssignmentStatus.CONFIRMED);

        review.unconfirm(OWNER, 70, 0, "Confirmed the wrong student");

        assertThat(a.getStatus()).isEqualTo(AssignmentStatus.RETURNED);
        assertThat(a.getFeedback()).isEqualTo("Confirmed the wrong student");
        assertThat(a.getConfirmedBy()).isNull();
        assertThat(a.getConfirmedAt()).isNull();
        assertThat(savedEvent().getAction()).isEqualTo(EventAction.UNCONFIRMED);
    }

    @Test
    void wrongStatesAreConflicts() {
        given(AssignmentStatus.NOT_STARTED);
        assertMessage(() -> review.confirm(OWNER, 70, 0), "Only submitted");
        assertMessage(() -> review.returnWithFeedback(OWNER, 70, 0, "x"), "Only submitted");
        assertMessage(() -> review.unconfirm(OWNER, 70, 0, "x"), "Only confirmed");
    }

    @Test
    void staleOrArchivedReviewsAreConflicts() {
        RequirementAssignment a = given(AssignmentStatus.SUBMITTED);
        ReflectionTestUtils.setField(a, "version", 2);
        assertMessage(() -> review.confirm(OWNER, 70, 1), "changed since you loaded it");

        ReflectionTestUtils.setField(a, "version", 0);
        archived(moa);
        assertMessage(() -> review.confirm(OWNER, 70, 0), "archived");
        verify(events, never()).save(any());
    }

    @Test
    void onlyTheRequirementOwnerMayReviewEvenAfterTheStudentIsReassigned() {
        // The student now belongs to NEW_COORDINATOR, but MOA is still owned by OWNER.
        given(AssignmentStatus.SUBMITTED);

        assertStatus(() -> review.confirm(NEW_COORDINATOR, 70, 0), HttpStatus.NOT_FOUND);
        assertStatus(() -> review.returnWithFeedback(NEW_COORDINATOR, 70, 0, "x"), HttpStatus.NOT_FOUND);
        review.confirm(OWNER, 70, 0);   // the owner keeps authority
    }

    @Test
    void studentListFlagsOverdueAndDueSoonWithTheManilaDate() {
        Requirement soon = requirement(51, OWNER, "Weekly report", LocalDate.of(2027, 1, 22));
        RequirementAssignment overdue = assignment(70, moa, STUDENT, AssignmentStatus.NOT_STARTED);   // due Jan 15
        RequirementAssignment dueSoon = assignment(71, soon, STUDENT, AssignmentStatus.RETURNED);     // due Jan 22
        when(assignments.findActiveByStudentId(STUDENT)).thenReturn(List.of(dueSoon, overdue));
        when(requirements.findAllById(any())).thenReturn(List.of(moa, soon));

        List<StudentRequirementResponse> list = student.list(STUDENT, false);

        assertThat(list).extracting(StudentRequirementResponse::title).containsExactly("Submit signed MOA", "Weekly report");
        assertThat(list.get(0).overdue()).isTrue();
        assertThat(list.get(1).dueSoon()).isTrue();
    }

    // ---- helpers ----

    private RequirementAssignment given(AssignmentStatus status) {
        RequirementAssignment a = assignment(70, moa, STUDENT, status);
        when(assignments.findByIdAndStudentId(70, STUDENT)).thenReturn(Optional.of(a));
        when(assignments.findById(70)).thenReturn(Optional.of(a));
        return a;
    }

    private RequirementEvent savedEvent() {
        ArgumentCaptor<RequirementEvent> captor = ArgumentCaptor.forClass(RequirementEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }
}
