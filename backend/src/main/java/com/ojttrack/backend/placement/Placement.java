package com.ojttrack.backend.placement;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A student's official OJT placement, created from one ACCEPTED application.
 * Placements are never deleted: ended ones stay as history (TERMINATED or COMPLETED).
 */
@Entity
@Table(name = "placements")
public class Placement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Integer applicationId;

    // Duplicated from the application on purpose: it lets the database enforce
    // "one ACTIVE placement per student". The composite FK in V4 keeps it consistent.
    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "required_hours", nullable = false)
    private Integer requiredHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    // The coordinator who confirmed completion; set exactly when COMPLETED (V5 CHECK).
    @Column(name = "completed_by")
    private Integer completedBy;

    // The job title, set by the coordinator at approval. NULL only for placements created
    // before V8; every new placement has one (activate below), and there is no setter.
    @Column(length = 150, updatable = false)
    private String role;

    protected Placement() {}   // JPA needs this

    /**
     * A new ACTIVE placement. The role is required and stored trimmed. A missing or blank
     * role is a programming error here (the request DTO and the service validate it first).
     */
    public static Placement activate(Integer applicationId, Integer studentId,
                                     LocalDate startDate, LocalDate endDate, Integer requiredHours,
                                     String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("A new placement needs a role");
        }
        Placement placement = new Placement();
        placement.role = role.strip();
        placement.applicationId = applicationId;
        placement.studentId = studentId;
        placement.startDate = startDate;
        placement.endDate = endDate;
        placement.requiredHours = requiredHours;
        placement.status = PlacementStatus.ACTIVE;
        return placement;
    }

    /** Ends this placement because the student switched to another one. */
    public void terminate() {
        if (status != PlacementStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE placement can be terminated");
        }
        status = PlacementStatus.TERMINATED;
        endedAt = Instant.now();
    }

    /**
     * The coordinator confirms the OJT is finished. Never automatic: the caller has
     * already checked the verified hours and that no logs are still pending.
     */
    public void complete(Integer coordinatorId) {
        if (status != PlacementStatus.ACTIVE) {
            throw new IllegalStateException("Only an ACTIVE placement can be completed");
        }
        status = PlacementStatus.COMPLETED;
        endedAt = Instant.now();
        completedBy = coordinatorId;
    }

    public Integer getCompletedBy() { return completedBy; }
    public Integer getId() { return id; }
    public Integer getApplicationId() { return applicationId; }
    public Integer getStudentId() { return studentId; }
    public PlacementStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getRequiredHours() { return requiredHours; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEndedAt() { return endedAt; }
}
