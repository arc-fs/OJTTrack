package com.ojttrack.backend.requirement;

import com.ojttrack.backend.requirement.RequirementResponse.ProgressCounts;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordinator management of requirements. Authorization (one rule, §C3): the authenticated
 * coordinator must OWN the requirement (requirements.coordinator_id), otherwise 404. When
 * assigning, each student must also currently be this coordinator's student.
 *
 * Order of checks in every change: owned (404) -> active, i.e. not archived (409) ->
 * version matches what the coordinator saw (409) -> the change itself.
 */
@Service
public class RequirementService {

    private final RequirementRepository requirements;
    private final RequirementAssignmentRepository assignments;
    private final RequirementEventRepository events;
    private final AppUserRepository users;
    private final Clock clock;

    public RequirementService(RequirementRepository requirements, RequirementAssignmentRepository assignments,
                              RequirementEventRepository events, AppUserRepository users, Clock clock) {
        this.requirements = requirements;
        this.assignments = assignments;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RequirementResponse> list(Integer coordinatorId, boolean includeArchived) {
        List<Requirement> owned = requirements.findByCoordinatorIdOrderByCreatedAtDesc(coordinatorId).stream()
                .filter(r -> includeArchived || !r.isArchived())
                .toList();
        Map<Integer, List<RequirementAssignment>> byRequirement = owned.isEmpty() ? Map.of()
                : assignments.findByRequirementIdIn(owned.stream().map(Requirement::getId).toList()).stream()
                        .collect(Collectors.groupingBy(RequirementAssignment::getRequirementId));
        return owned.stream()
                .map(r -> RequirementResponse.of(r, counts(byRequirement.getOrDefault(r.getId(), List.of()))))
                .toList();
    }

    /** Assigns to the chosen students, or to ALL students currently assigned to the coordinator. */
    @Transactional
    public RequirementResponse create(Integer coordinatorId, CreateRequirementRequest request) {
        List<AppUser> students = request.studentIds() == null || request.studentIds().isEmpty()
                ? users.findByCoordinatorIdOrderByFullNameAsc(coordinatorId)
                : ownStudents(coordinatorId, request.studentIds());
        Requirement requirement = requirements.saveAndFlush(
                Requirement.create(coordinatorId, request.title(), request.description(), request.dueDate()));
        List<RequirementAssignment> created = assignments.saveAll(students.stream()
                .map(s -> RequirementAssignment.assign(requirement, s.getId()))
                .toList());
        return RequirementResponse.of(requirement, counts(created));
    }

    @Transactional
    public RequirementResponse update(Integer coordinatorId, Integer id, UpdateRequirementRequest request) {
        Requirement requirement = requireOwned(coordinatorId, id);
        requireActive(requirement);
        requireVersion(requirement, request.version());
        requirement.edit(request.title(), request.description(), request.dueDate());
        requirements.saveAndFlush(requirement);
        return RequirementResponse.of(requirement, counts(assignments.findByRequirementId(id)));
    }

    /** Add more of the coordinator's students; students already assigned are skipped. */
    @Transactional
    public RequirementResponse assign(Integer coordinatorId, Integer id, AssignStudentsRequest request) {
        Requirement requirement = requireOwned(coordinatorId, id);
        requireActive(requirement);
        List<AppUser> students = ownStudents(coordinatorId, request.studentIds());
        Set<Integer> already = assignments.findByRequirementId(id).stream()
                .map(RequirementAssignment::getStudentId).collect(Collectors.toSet());
        assignments.saveAll(students.stream()
                .filter(s -> !already.contains(s.getId()))
                .map(s -> RequirementAssignment.assign(requirement, s.getId()))
                .toList());
        return RequirementResponse.of(requirement, counts(assignments.findByRequirementId(id)));
    }

    /** Only while the student hasn't submitted anything for it (no history to lose). */
    @Transactional
    public void removeStudent(Integer coordinatorId, Integer id, Integer studentId) {
        Requirement requirement = requireOwned(coordinatorId, id);
        requireActive(requirement);
        RequirementAssignment assignment = assignments.findByRequirementIdAndStudentId(id, studentId)
                .orElseThrow(() -> notFound("This student isn't assigned to the requirement"));
        if (events.existsByAssignmentId(assignment.getId())) {
            throw conflict("This student has already submitted this requirement, so they can't be removed. "
                    + "Archive the requirement instead");
        }
        assignments.delete(assignment);
    }

    @Transactional
    public RequirementResponse archive(Integer coordinatorId, Integer id, Integer version) {
        Requirement requirement = requireOwned(coordinatorId, id);
        if (requirement.isArchived()) {
            throw conflict("Already archived");
        }
        requireVersion(requirement, version);
        requirement.archive(Instant.now(clock));
        requirements.saveAndFlush(requirement);
        return RequirementResponse.of(requirement, counts(assignments.findByRequirementId(id)));
    }

    /**
     * Delete only when nobody ever submitted anything: then the database removes the untouched
     * assignments too (ON DELETE CASCADE). With history, 409: archive instead. Even if this check
     * were skipped, the RESTRICT foreign key on requirement_events makes PostgreSQL refuse.
     */
    @Transactional
    public void delete(Integer coordinatorId, Integer id, Integer version) {
        Requirement requirement = requireOwned(coordinatorId, id);
        requireVersion(requirement, version);
        if (events.existsByRequirementId(id)) {
            throw conflict("Students have already submitted this requirement, so it has history and "
                    + "can't be deleted. Archive it instead");
        }
        requirements.delete(requirement);
    }

    /** Every student's progress on one requirement (archived ones too: history stays viewable). */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(Integer coordinatorId, Integer id) {
        Requirement requirement = requireOwned(coordinatorId, id);
        LocalDate today = LocalDate.now(clock);
        List<RequirementAssignment> found = assignments.findByRequirementId(id);
        Map<Integer, String> names = RequirementHistory.names(found.stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getStudentId(), a.getConfirmedBy())).toList(), users);
        Map<Integer, List<EventResponse>> history = RequirementHistory.byAssignment(
                found.stream().map(RequirementAssignment::getId).toList(), events, users);
        return found.stream()
                .sorted(java.util.Comparator.comparing(a -> Objects.requireNonNullElse(names.get(a.getStudentId()), "")))
                .map(a -> new AssignmentResponse(a.getId(), a.getStudentId(), names.get(a.getStudentId()), a.getStatus(),
                        a.getNote(), a.getFeedback(), a.getSubmittedAt(), names.get(a.getConfirmedBy()),
                        a.getConfirmedAt(), a.getVersion(),
                        RequirementRules.isOverdue(a.getStatus(), requirement.getDueDate(), requirement.isArchived(), today),
                        history.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    /**
     * Completion review (Progress tab): one of MY current students' progress on the
     * non-archived requirements I own. Informational only: never blocks completion.
     */
    @Transactional(readOnly = true)
    public RequirementSummary studentSummary(Integer coordinatorId, Integer studentId) {
        users.findById(studentId)
                .filter(s -> coordinatorId.equals(s.getCoordinatorId()))
                .orElseThrow(() -> notFound("Student not found"));
        List<RequirementAssignment> active = assignments.findActiveForCoordinator(coordinatorId, List.of(studentId));
        Map<Integer, Requirement> byId = requirements.findAllById(
                        active.stream().map(RequirementAssignment::getRequirementId).toList())
                .stream().collect(Collectors.toMap(Requirement::getId, Function.identity()));
        return RequirementSummary.of(active, byId, LocalDate.now(clock));
    }

    // ---- helpers ----

    /** The one ownership rule: requirements.coordinator_id must be the authenticated coordinator. */
    Requirement requireOwned(Integer coordinatorId, Integer requirementId) {
        return requirements.findById(requirementId)
                .filter(r -> r.isOwnedBy(coordinatorId))
                .orElseThrow(() -> notFound("Requirement not found"));   // same as "doesn't exist"
    }

    private static void requireActive(Requirement requirement) {
        if (requirement.isArchived()) {
            throw conflict("This requirement is archived and can't be changed");
        }
    }

    private static void requireVersion(Requirement requirement, Integer version) {
        if (!Objects.equals(requirement.getVersion(), version)) {
            throw conflict("This requirement changed since you loaded it. Reload and try again");
        }
    }

    /** Each id must be a student currently assigned to this coordinator. */
    private List<AppUser> ownStudents(Integer coordinatorId, List<Integer> studentIds) {
        List<Integer> distinct = studentIds.stream().distinct().toList();
        Map<Integer, AppUser> found = users.findAllById(distinct).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        for (Integer id : distinct) {
            AppUser student = found.get(id);
            if (student == null || !coordinatorId.equals(student.getCoordinatorId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student " + id + " is not one of your students");
            }
        }
        return distinct.stream().map(found::get).toList();
    }

    static ProgressCounts counts(List<RequirementAssignment> list) {
        int notStarted = 0, submitted = 0, returned = 0, confirmed = 0;
        for (RequirementAssignment a : list) {
            switch (a.getStatus()) {
                case NOT_STARTED -> notStarted++;
                case SUBMITTED -> submitted++;
                case RETURNED -> returned++;
                case CONFIRMED -> confirmed++;
            }
        }
        return new ProgressCounts(notStarted, submitted, returned, confirmed);
    }

    static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
