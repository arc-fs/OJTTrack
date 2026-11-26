package com.ojttrack.backend.requirement;

import static com.ojttrack.backend.requirement.RequirementService.conflict;
import static com.ojttrack.backend.requirement.RequirementService.notFound;

import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinator review: confirm, return, un-confirm. Only the requirement's OWNER may review
 * (requirements.coordinator_id), whatever the student's current coordinator is; otherwise 404.
 * Then: archived (409) -> version the coordinator saw (409) -> state (409). The assignment
 * change and its history event are saved in the same transaction.
 */
@Service
public class RequirementReviewService {

    private final RequirementRepository requirements;
    private final RequirementAssignmentRepository assignments;
    private final RequirementEventRepository events;
    private final AppUserRepository users;
    private final Clock clock;

    public RequirementReviewService(RequirementRepository requirements, RequirementAssignmentRepository assignments,
                                    RequirementEventRepository events, AppUserRepository users, Clock clock) {
        this.requirements = requirements;
        this.assignments = assignments;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public AssignmentResponse confirm(Integer coordinatorId, Integer assignmentId, Integer version) {
        Review review = load(coordinatorId, assignmentId, version);
        if (review.assignment.getStatus() != AssignmentStatus.SUBMITTED) {
            throw conflict("Only submitted requirements can be confirmed");
        }
        Instant now = Instant.now(clock);
        review.assignment.confirm(review.requirement, coordinatorId, now);
        return save(review, EventAction.CONFIRMED, coordinatorId, null, now);
    }

    @Transactional
    public AssignmentResponse returnWithFeedback(Integer coordinatorId, Integer assignmentId, Integer version,
                                                 String feedback) {
        Review review = load(coordinatorId, assignmentId, version);
        if (review.assignment.getStatus() != AssignmentStatus.SUBMITTED) {
            throw conflict("Only submitted requirements can be returned");
        }
        String text = feedback.trim();
        review.assignment.returnWithFeedback(review.requirement, text);
        return save(review, EventAction.RETURNED, coordinatorId, text, Instant.now(clock));
    }

    /** Undo a mistaken confirmation: back to RETURNED with the reason the student will see. */
    @Transactional
    public AssignmentResponse unconfirm(Integer coordinatorId, Integer assignmentId, Integer version, String reason) {
        Review review = load(coordinatorId, assignmentId, version);
        if (review.assignment.getStatus() != AssignmentStatus.CONFIRMED) {
            throw conflict("Only confirmed requirements can be un-confirmed");
        }
        String text = reason.trim();
        review.assignment.unconfirm(review.requirement, text);
        return save(review, EventAction.UNCONFIRMED, coordinatorId, text, Instant.now(clock));
    }

    private record Review(RequirementAssignment assignment, Requirement requirement) {}

    private Review load(Integer coordinatorId, Integer assignmentId, Integer version) {
        RequirementAssignment assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> notFound("Requirement assignment not found"));
        Requirement requirement = requirements.findById(assignment.getRequirementId())
                .filter(r -> r.isOwnedBy(coordinatorId))
                .orElseThrow(() -> notFound("Requirement assignment not found"));   // not the owner: reveal nothing
        if (requirement.isArchived()) {
            throw conflict("This requirement is archived and can't be changed");
        }
        if (!Objects.equals(assignment.getVersion(), version)) {
            throw conflict("This submission changed since you loaded it. Reload and review it again");
        }
        return new Review(assignment, requirement);
    }

    private AssignmentResponse save(Review review, EventAction action, Integer actorId, String text, Instant now) {
        RequirementAssignment saved = assignments.saveAndFlush(review.assignment);
        events.save(RequirementEvent.record(saved, action, actorId, text, now));
        // Arrays.asList allows the null confirmedBy (List.of doesn't); names() skips nulls.
        Map<Integer, String> names = RequirementHistory.names(
                java.util.Arrays.asList(saved.getStudentId(), saved.getConfirmedBy()), users);
        return new AssignmentResponse(saved.getId(), saved.getStudentId(), names.get(saved.getStudentId()),
                saved.getStatus(), saved.getNote(), saved.getFeedback(), saved.getSubmittedAt(),
                names.get(saved.getConfirmedBy()), saved.getConfirmedAt(), saved.getVersion(),
                RequirementRules.isOverdue(saved.getStatus(), review.requirement.getDueDate(), false, LocalDate.now(clock)),
                RequirementHistory.byAssignment(List.of(saved.getId()), events, users).getOrDefault(saved.getId(), List.of()));
    }
}
