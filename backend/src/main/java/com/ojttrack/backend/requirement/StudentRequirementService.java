package com.ojttrack.backend.requirement;

import static com.ojttrack.backend.requirement.RequirementService.conflict;
import static com.ojttrack.backend.requirement.RequirementService.notFound;

import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student's side: see assigned requirements and submit them. Students act only on their
 * OWN assignments (student id from the session), whoever their coordinator currently is.
 */
@Service
public class StudentRequirementService {

    private final RequirementRepository requirements;
    private final RequirementAssignmentRepository assignments;
    private final RequirementEventRepository events;
    private final AppUserRepository users;
    private final Clock clock;

    public StudentRequirementService(RequirementRepository requirements, RequirementAssignmentRepository assignments,
                                     RequirementEventRepository events, AppUserRepository users, Clock clock) {
        this.requirements = requirements;
        this.assignments = assignments;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    /** Active requirements by default; includeArchived adds archived ones as read-only history. */
    @Transactional(readOnly = true)
    public List<StudentRequirementResponse> list(Integer studentId, boolean includeArchived) {
        List<RequirementAssignment> mine = includeArchived
                ? assignments.findByStudentId(studentId)
                : assignments.findActiveByStudentId(studentId);
        Map<Integer, Requirement> byId = requirements.findAllById(
                        mine.stream().map(RequirementAssignment::getRequirementId).toList())
                .stream().collect(Collectors.toMap(Requirement::getId, Function.identity()));
        Map<Integer, List<EventResponse>> history = RequirementHistory.byAssignment(
                mine.stream().map(RequirementAssignment::getId).toList(), events, users);
        LocalDate today = LocalDate.now(clock);
        return mine.stream()
                .map(a -> view(a, byId.get(a.getRequirementId()), history.getOrDefault(a.getId(), List.of()), today))
                // undated last; then by due date; archived at the end
                .sorted(Comparator.comparing(StudentRequirementResponse::archived)
                        .thenComparing(StudentRequirementResponse::dueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** From NOT_STARTED, or resubmitting after RETURNED, with the version the student saw. */
    @Transactional
    public StudentRequirementResponse submit(Integer studentId, Integer assignmentId, Integer version, String note) {
        RequirementAssignment assignment = assignments.findByIdAndStudentId(assignmentId, studentId)
                .orElseThrow(() -> notFound("Requirement not found"));
        Requirement requirement = requirements.findById(assignment.getRequirementId())
                .orElseThrow(() -> notFound("Requirement not found"));
        if (requirement.isArchived()) {
            throw conflict("This requirement is archived and can't be changed");
        }
        if (!Objects.equals(assignment.getVersion(), version)) {
            throw conflict("This requirement changed since you loaded it. Reload and try again");
        }
        if (assignment.getStatus() == AssignmentStatus.SUBMITTED) {
            throw conflict("This requirement is already submitted and waiting for review");
        }
        if (assignment.getStatus() == AssignmentStatus.CONFIRMED) {
            throw conflict("This requirement is already confirmed");
        }
        String text = note == null || note.isBlank() ? null : note.trim();
        Instant now = Instant.now(clock);
        assignment.submit(requirement, text, now);
        RequirementAssignment saved = assignments.saveAndFlush(assignment);
        events.save(RequirementEvent.record(saved, EventAction.SUBMITTED, studentId, text, now));
        return view(saved, requirement, RequirementHistory.byAssignment(List.of(saved.getId()), events, users)
                .getOrDefault(saved.getId(), List.of()), LocalDate.now(clock));
    }

    private static StudentRequirementResponse view(RequirementAssignment a, Requirement r,
                                                   List<EventResponse> history, LocalDate today) {
        return new StudentRequirementResponse(a.getId(), r.getId(), r.getTitle(), r.getDescription(), r.getDueDate(),
                r.isArchived(), a.getStatus(), a.getNote(), a.getFeedback(), a.getSubmittedAt(), a.getConfirmedAt(),
                a.getVersion(),
                RequirementRules.isOverdue(a.getStatus(), r.getDueDate(), r.isArchived(), today),
                RequirementRules.isDueSoon(a.getStatus(), r.getDueDate(), r.isArchived(), today),
                history);
    }
}
