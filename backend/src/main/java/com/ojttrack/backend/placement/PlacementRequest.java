package com.ojttrack.backend.placement;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A student's request to make one of their ACCEPTED applications their placement.
 * currentPlacementId == null means a first activation; otherwise it is a change
 * request that would replace that placement. Only a coordinator decides it.
 */
@Entity
@Table(name = "placement_requests")
public class PlacementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Integer applicationId;

    @Column(name = "current_placement_id", updatable = false)
    private Integer currentPlacementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementRequestStatus status;

    @Column(updatable = false)
    private String reason;

    @Column(name = "decision_note")
    private String decisionNote;

    @Column(name = "decided_by")
    private Integer decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PlacementRequest() {}   // JPA needs this

    public static PlacementRequest submit(Integer studentId, Integer applicationId,
                                          Integer currentPlacementId, String reason) {
        PlacementRequest request = new PlacementRequest();
        request.studentId = studentId;
        request.applicationId = applicationId;
        request.currentPlacementId = currentPlacementId;
        request.reason = reason;
        request.status = PlacementRequestStatus.PENDING;
        return request;
    }

    public void approve(Integer coordinatorId, String note) {
        decide(PlacementRequestStatus.APPROVED, coordinatorId, note);
    }

    public void reject(Integer coordinatorId, String note) {
        decide(PlacementRequestStatus.REJECTED, coordinatorId, note);
    }

    private void decide(PlacementRequestStatus decision, Integer coordinatorId, String note) {
        if (status != PlacementRequestStatus.PENDING) {
            throw new IllegalStateException("This request has already been decided");
        }
        status = decision;
        decidedBy = coordinatorId;
        decidedAt = Instant.now();
        decisionNote = note;
    }

    public boolean isChange() { return currentPlacementId != null; }

    public Integer getId() { return id; }
    public Integer getStudentId() { return studentId; }
    public Integer getApplicationId() { return applicationId; }
    public Integer getCurrentPlacementId() { return currentPlacementId; }
    public PlacementRequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getDecisionNote() { return decisionNote; }
    public Integer getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
