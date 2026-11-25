package com.ojttrack.backend.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ojttrack.backend.TestUsers;
import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Unit tests for the placement rules. Repositories are mocks: no Spring, no database. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlacementServiceTest {

    static final int COORDINATOR = 1;
    static final int OTHER_COORDINATOR = 2;
    static final int STUDENT = 10;
    static final LocalDate START = LocalDate.of(2027, 1, 4);
    static final LocalDate END = LocalDate.of(2027, 4, 30);

    @Mock PlacementRepository placements;
    @Mock PlacementRequestRepository requests;
    @Mock JobApplicationRepository applications;
    @Mock AppUserRepository users;

    PlacementService service;

    @BeforeEach
    void setUp() {
        service = new PlacementService(placements, requests, applications, users);
        when(users.findById(STUDENT)).thenReturn(Optional.of(TestUsers.studentEntity(STUDENT, COORDINATOR)));
        when(requests.save(any(PlacementRequest.class))).thenAnswer(call -> call.getArgument(0));
        when(placements.save(any(Placement.class))).thenAnswer(call -> call.getArgument(0));
        when(placements.saveAndFlush(any(Placement.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- submit ----

    @Test
    void submitRejectsAnotherStudentsApplication() {
        givenApplication(100, 99, ApplicationStatus.ACCEPTED);   // belongs to student 99

        assertStatus(() -> service.submit(STUDENT, 100, null), HttpStatus.NOT_FOUND);
        verify(requests, never()).save(any());
    }

    @Test
    void submitRejectsApplicationThatIsNotAccepted() {
        givenApplication(100, STUDENT, ApplicationStatus.OFFERED);

        assertStatus(() -> service.submit(STUDENT, 100, null), HttpStatus.CONFLICT);
        verify(requests, never()).save(any());
    }

    @Test
    void submitRejectsApplicationAlreadyUsedForAPlacement() {
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);
        when(placements.existsByApplicationId(100)).thenReturn(true);

        assertStatus(() -> service.submit(STUDENT, 100, null), HttpStatus.CONFLICT);
    }

    @Test
    void submitRejectsSecondPendingRequest() {
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);
        when(requests.existsByStudentIdAndStatus(STUDENT, PlacementRequestStatus.PENDING)).thenReturn(true);

        assertStatus(() -> service.submit(STUDENT, 100, null), HttpStatus.CONFLICT);
        verify(requests, never()).save(any());
    }

    @Test
    void submitWithoutActivePlacementIsAnActivationRequest() {
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);

        PlacementRequestResponse response = service.submit(STUDENT, 100, "  first placement  ");

        assertThat(response.type()).isEqualTo("ACTIVATION");
        assertThat(response.status()).isEqualTo(PlacementRequestStatus.PENDING);
        assertThat(response.reason()).isEqualTo("first placement");
    }

    @Test
    void submitWithActivePlacementIsAChangeRequest() {
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);
        Placement active = placement(500, 90);
        when(placements.findByStudentIdAndStatus(STUDENT, PlacementStatus.ACTIVE)).thenReturn(Optional.of(active));

        PlacementRequestResponse response = service.submit(STUDENT, 100, null);

        ArgumentCaptor<PlacementRequest> saved = ArgumentCaptor.forClass(PlacementRequest.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().getCurrentPlacementId()).isEqualTo(500);
        assertThat(response.type()).isEqualTo("CHANGE");
        // Rule 8: submitting never touches the current placement.
        assertThat(active.getStatus()).isEqualTo(PlacementStatus.ACTIVE);
    }

    // ---- approve ----

    @Test
    void approveRejectsRequestOfAnotherCoordinatorsStudent() {
        givenPendingRequest(7, 100, null);

        assertStatus(() -> service.approve(OTHER_COORDINATOR, 7, START, END, 486, null), HttpStatus.NOT_FOUND);
        verify(placements, never()).save(any());
    }

    @Test
    void approveRejectsAlreadyDecidedRequest() {
        PlacementRequest request = givenPendingRequest(7, 100, null);
        request.reject(COORDINATOR, "no");

        assertStatus(() -> service.approve(COORDINATOR, 7, START, END, 486, null), HttpStatus.CONFLICT);
        verify(placements, never()).save(any());
    }

    @Test
    void approveRejectsEndDateBeforeStartDate() {
        givenPendingRequest(7, 100, null);
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);

        assertStatus(() -> service.approve(COORDINATOR, 7, END, START, 486, null), HttpStatus.BAD_REQUEST);
        verify(placements, never()).save(any());
    }

    @Test
    void approveRejectsApplicationNoLongerAccepted() {
        givenPendingRequest(7, 100, null);
        givenApplication(100, STUDENT, ApplicationStatus.WITHDRAWN);

        assertStatus(() -> service.approve(COORDINATOR, 7, START, END, 486, null), HttpStatus.CONFLICT);
        verify(placements, never()).save(any());
    }

    @Test
    void approveRejectsStaleRequest() {
        // Submitted as an activation, but the student has had an ACTIVE placement since.
        givenPendingRequest(7, 100, null);
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);
        givenActivePlacementForUpdate(placement(500, 90));

        assertStatus(() -> service.approve(COORDINATOR, 7, START, END, 486, null), HttpStatus.CONFLICT);
        verify(placements, never()).save(any());
        verify(placements, never()).saveAndFlush(any());
    }

    @Test
    void approveActivationCreatesActivePlacement() {
        PlacementRequest request = givenPendingRequest(7, 100, null);
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);

        PlacementRequestResponse response = service.approve(COORDINATOR, 7, START, END, 486, "  ok  ");

        ArgumentCaptor<Placement> saved = ArgumentCaptor.forClass(Placement.class);
        verify(placements).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PlacementStatus.ACTIVE);
        assertThat(saved.getValue().getApplicationId()).isEqualTo(100);
        assertThat(saved.getValue().getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getValue().getRequiredHours()).isEqualTo(486);
        assertThat(request.getStatus()).isEqualTo(PlacementRequestStatus.APPROVED);
        assertThat(request.getDecidedBy()).isEqualTo(COORDINATOR);
        assertThat(response.decisionNote()).isEqualTo("ok");
    }

    @Test
    void approveChangeTerminatesOldPlacementBeforeActivatingNewOne() {
        Placement old = placement(500, 90);
        givenPendingRequest(7, 100, 500);
        givenApplication(100, STUDENT, ApplicationStatus.ACCEPTED);
        givenActivePlacementForUpdate(old);

        service.approve(COORDINATOR, 7, START, END, 486, null);

        assertThat(old.getStatus()).isEqualTo(PlacementStatus.TERMINATED);
        assertThat(old.getEndedAt()).isNotNull();
        // The old row must be written before the new ACTIVE row is inserted.
        InOrder order = inOrder(placements);
        order.verify(placements).saveAndFlush(old);
        order.verify(placements).save(any(Placement.class));
    }

    // ---- reject ----

    @Test
    void rejectLeavesPlacementsUntouched() {
        PlacementRequest request = givenPendingRequest(7, 100, 500);

        service.reject(COORDINATOR, 7, "stay where you are");

        assertThat(request.getStatus()).isEqualTo(PlacementRequestStatus.REJECTED);
        assertThat(request.getDecisionNote()).isEqualTo("stay where you are");
        verify(placements, never()).save(any());
        verify(placements, never()).saveAndFlush(any());
    }

    @Test
    void rejectRefusesAnotherCoordinatorsStudent() {
        PlacementRequest request = givenPendingRequest(7, 100, null);

        assertStatus(() -> service.reject(OTHER_COORDINATOR, 7, null), HttpStatus.NOT_FOUND);
        assertThat(request.getStatus()).isEqualTo(PlacementRequestStatus.PENDING);
    }

    // ---- helpers ----

    private void givenApplication(int id, int studentId, ApplicationStatus status) {
        JobApplication app = mock(JobApplication.class);
        when(app.getId()).thenReturn(id);
        when(app.getStudentId()).thenReturn(studentId);
        when(app.getStatus()).thenReturn(status);
        when(app.getCompanyName()).thenReturn("Acme");
        when(applications.findByIdForUpdate(id)).thenReturn(Optional.of(app));
        when(applications.findById(id)).thenReturn(Optional.of(app));
    }

    private PlacementRequest givenPendingRequest(int id, int applicationId, Integer currentPlacementId) {
        PlacementRequest request = PlacementRequest.submit(STUDENT, applicationId, currentPlacementId, null);
        ReflectionTestUtils.setField(request, "id", id);
        when(requests.findByIdForUpdate(id)).thenReturn(Optional.of(request));
        return request;
    }

    private void givenActivePlacementForUpdate(Placement placement) {
        when(placements.findForUpdateByStudentIdAndStatus(STUDENT, PlacementStatus.ACTIVE))
                .thenReturn(Optional.of(placement));
    }

    private static Placement placement(int id, int applicationId) {
        Placement placement = Placement.activate(applicationId, STUDENT, START, END, 486);
        ReflectionTestUtils.setField(placement, "id", id);
        return placement;
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo((HttpStatusCode) expected);
    }
}
