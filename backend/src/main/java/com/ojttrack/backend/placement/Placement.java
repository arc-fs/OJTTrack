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

    protected Placement() {}   // JPA needs this

    public static Placement activate(Integer applicationId, Integer studentId,
                                     LocalDate startDate, LocalDate endDate, Integer requiredHours) {
        Placement placement = new Placement();
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

    public Integer getId() { return id; }
    public Integer getApplicationId() { return applicationId; }
    public Integer getStudentId() { return studentId; }
    public PlacementStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getRequiredHours() { return requiredHours; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEndedAt() { return endedAt; }
}
