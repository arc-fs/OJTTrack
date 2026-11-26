package com.ojttrack.backend.requirement;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One student's progress on one requirement: the CURRENT state. Every change is also
 * recorded as a RequirementEvent (in the same transaction) so the history is complete.
 *
 * Each transition receives the Requirement and refuses if it is archived (read-only),
 * on top of the same check in the service.
 */
@Entity
@Table(name = "requirement_assignments")
public class RequirementAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "requirement_id", nullable = false, updatable = false)
    private Integer requirementId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    private String note;        // latest submission note (history keeps every one)

    private String feedback;    // latest return / un-confirm reason

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "confirmed_by")
    private Integer confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RequirementAssignment() {}   // JPA needs this

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static RequirementAssignment assign(Requirement requirement, Integer studentId) {
        requirement.requireActive();
        RequirementAssignment assignment = new RequirementAssignment();
        assignment.requirementId = requirement.getId();
        assignment.studentId = studentId;
        assignment.status = AssignmentStatus.NOT_STARTED;
        return assignment;
    }

    /** The student reports it done: from NOT_STARTED, or resubmitting after RETURNED. */
    public void submit(Requirement requirement, String note, Instant now) {
        requirement.requireActive();
        if (status != AssignmentStatus.NOT_STARTED && status != AssignmentStatus.RETURNED) {
            throw new IllegalStateException(status == AssignmentStatus.CONFIRMED
                    ? "This requirement is already confirmed"
                    : "This requirement is already submitted and waiting for review");
        }
        status = AssignmentStatus.SUBMITTED;
        this.note = note;
        submittedAt = now;
    }

    /** The coordinator makes it official. */
    public void confirm(Requirement requirement, Integer coordinatorId, Instant now) {
        requirement.requireActive();
        requireStatus(AssignmentStatus.SUBMITTED, "Only submitted requirements can be confirmed");
        status = AssignmentStatus.CONFIRMED;
        confirmedBy = coordinatorId;
        confirmedAt = now;
    }

    /** The coordinator sends it back with feedback; the student fixes and resubmits. */
    public void returnWithFeedback(Requirement requirement, String feedback) {
        requirement.requireActive();
        requireStatus(AssignmentStatus.SUBMITTED, "Only submitted requirements can be returned");
        status = AssignmentStatus.RETURNED;
        this.feedback = feedback;
    }

    /** Undo a mistaken confirmation: CONFIRMED -> RETURNED, with the reason as feedback. */
    public void unconfirm(Requirement requirement, String reason) {
        requirement.requireActive();
        requireStatus(AssignmentStatus.CONFIRMED, "Only confirmed requirements can be un-confirmed");
        status = AssignmentStatus.RETURNED;
        feedback = reason;
        confirmedBy = null;
        confirmedAt = null;
    }

    private void requireStatus(AssignmentStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public Integer getId() { return id; }
    public Integer getRequirementId() { return requirementId; }
    public Integer getStudentId() { return studentId; }
    public AssignmentStatus getStatus() { return status; }
    public String getNote() { return note; }
    public String getFeedback() { return feedback; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Integer getConfirmedBy() { return confirmedBy; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
