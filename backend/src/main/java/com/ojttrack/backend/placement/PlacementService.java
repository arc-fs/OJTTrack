package com.ojttrack.backend.placement;

import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business rules for placements. Each public method runs in one database transaction:
 * either every change it makes is saved, or (on any exception) none are.
 *
 * Status codes used: 404 = not found or not yours, 409 = conflicts with the current
 * state, 400 = invalid input.
 */
@Service
public class PlacementService {

    private final PlacementRepository placements;
    private final PlacementRequestRepository requests;
    private final JobApplicationRepository applications;
    private final AppUserRepository users;

    public PlacementService(PlacementRepository placements, PlacementRequestRepository requests,
                            JobApplicationRepository applications, AppUserRepository users) {
        this.placements = placements;
        this.requests = requests;
        this.applications = applications;
        this.users = users;
    }

    // ---- Student actions ----

    /** Rules 5 and 6: ask to make an ACCEPTED application the student's placement. */
    @Transactional
    public PlacementRequestResponse submit(Integer studentId, Integer applicationId, String reason) {
        // Lock the application so its status can't change while we check it.
        JobApplication application = applications.findByIdForUpdate(applicationId)
                .filter(app -> app.getStudentId().equals(studentId))
                .orElseThrow(() -> notFound("Application not found"));
        requireUsableForPlacement(application);
        // Without this check, "no ACTIVE placement" after completion would look like
        // "never had one" and allow a second OJT placement.
        if (placements.existsByStudentIdAndStatus(studentId, PlacementStatus.COMPLETED)) {
            throw conflict("You have already completed your OJT placement");
        }
        if (requests.existsByStudentIdAndStatus(studentId, PlacementRequestStatus.PENDING)) {
            throw conflict("You already have a pending placement request");
        }
        // With an ACTIVE placement this becomes a change request; without one, an activation.
        Integer currentPlacementId = placements
                .findByStudentIdAndStatus(studentId, PlacementStatus.ACTIVE)
                .map(Placement::getId)
                .orElse(null);

        PlacementRequest saved = requests.save(
                PlacementRequest.submit(studentId, applicationId, currentPlacementId, blankToNull(reason)));
        return PlacementRequestResponse.of(saved, application.getCompanyName(), nameOf(studentId));
    }

    @Transactional(readOnly = true)
    public List<PlacementResponse> placementsFor(Integer studentId) {
        Map<Integer, String> companies = companyNames(applications.findByStudentId(studentId));
        return placements.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(p -> PlacementResponse.of(p, companies.get(p.getApplicationId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlacementRequestResponse> requestsFor(Integer studentId) {
        Map<Integer, String> companies = companyNames(applications.findByStudentId(studentId));
        String studentName = nameOf(studentId);
        return requests.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(r -> PlacementRequestResponse.of(r, companies.get(r.getApplicationId()), studentName))
                .toList();
    }

    // ---- Coordinator actions ----

    @Transactional(readOnly = true)
    public List<PlacementRequestResponse> pendingRequestsFor(Integer coordinatorId) {
        List<PlacementRequest> pending = requests.findForCoordinator(coordinatorId, PlacementRequestStatus.PENDING);
        Map<Integer, String> companies = companyNames(
                applications.findAllById(pending.stream().map(PlacementRequest::getApplicationId).toList()));
        Map<Integer, String> names = users.findAllById(pending.stream().map(PlacementRequest::getStudentId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return pending.stream()
                .map(r -> PlacementRequestResponse.of(r, companies.get(r.getApplicationId()), names.get(r.getStudentId())))
                .toList();
    }

    /**
     * Rules 7–10: approve a request. For a change request the old placement is
     * TERMINATED and the new one becomes ACTIVE in the same transaction.
     * Rows are locked in a fixed order (request → application → active placement)
     * so two concurrent approvals queue up instead of deadlocking.
     */
    @Transactional
    public PlacementRequestResponse approve(Integer coordinatorId, Integer requestId,
                                            LocalDate startDate, LocalDate endDate,
                                            Integer requiredHours, String role, String note) {
        PlacementRequest request = lockPendingRequest(coordinatorId, requestId);
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the placement's role (job title)");
        }

        // Re-check the application now: the student may have changed it since submitting.
        JobApplication application = applications.findByIdForUpdate(request.getApplicationId())
                .orElseThrow(() -> conflict("The application no longer exists"));
        requireUsableForPlacement(application);

        Optional<Placement> current = placements
                .findForUpdateByStudentIdAndStatus(request.getStudentId(), PlacementStatus.ACTIVE);
        Integer currentId = current.map(Placement::getId).orElse(null);
        if (!Objects.equals(currentId, request.getCurrentPlacementId())) {
            throw conflict("The student's placement changed after this request was made; reject it and ask for a new one");
        }

        current.ifPresent(old -> {
            old.terminate();
            // Write the termination first. At flush time Hibernate sends INSERTs before
            // UPDATEs, which would briefly leave two ACTIVE rows and violate the
            // one-active-placement unique index.
            placements.saveAndFlush(old);
        });
        placements.save(Placement.activate(
                application.getId(), request.getStudentId(), startDate, endDate, requiredHours, role));
        request.approve(coordinatorId, blankToNull(note));

        return PlacementRequestResponse.of(request, application.getCompanyName(), nameOf(request.getStudentId()));
    }

    /** Rule 9: rejecting changes nothing except the request itself. */
    @Transactional
    public PlacementRequestResponse reject(Integer coordinatorId, Integer requestId, String note) {
        PlacementRequest request = lockPendingRequest(coordinatorId, requestId);
        request.reject(coordinatorId, blankToNull(note));
        String company = applications.findById(request.getApplicationId())
                .map(JobApplication::getCompanyName).orElse(null);
        return PlacementRequestResponse.of(request, company, nameOf(request.getStudentId()));
    }

    // ---- Helpers ----

    /** The request must exist, belong to one of this coordinator's students, and be PENDING. */
    private PlacementRequest lockPendingRequest(Integer coordinatorId, Integer requestId) {
        PlacementRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> notFound("Placement request not found"));
        boolean ownStudent = users.findById(request.getStudentId())
                .map(student -> coordinatorId.equals(student.getCoordinatorId()))
                .orElse(false);
        if (!ownStudent) {
            // Same 404 as "doesn't exist": don't reveal other coordinators' requests.
            throw notFound("Placement request not found");
        }
        if (request.getStatus() != PlacementRequestStatus.PENDING) {
            throw conflict("This request has already been decided");
        }
        return request;
    }

    /** Only ACCEPTED applications that were never used for a placement qualify. */
    private void requireUsableForPlacement(JobApplication application) {
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw conflict("Only ACCEPTED applications can become placements");
        }
        if (placements.existsByApplicationId(application.getId())) {
            throw conflict("This application has already been used for a placement");
        }
    }

    private String nameOf(Integer userId) {
        return users.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    private static Map<Integer, String> companyNames(Collection<JobApplication> apps) {
        return apps.stream().collect(Collectors.toMap(JobApplication::getId, JobApplication::getCompanyName));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
