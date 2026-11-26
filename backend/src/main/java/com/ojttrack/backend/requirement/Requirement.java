package com.ojttrack.backend.requirement;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A school requirement or OJT task, owned by the coordinator who created it.
 * Archived requirements are read-only and hidden from active views; their history stays.
 */
@Entity
@Table(name = "requirements")
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // The owner: only this coordinator may manage the requirement or review its assignments.
    @Column(name = "coordinator_id", nullable = false, updatable = false)
    private Integer coordinatorId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "archived_at")
    private Instant archivedAt;

    // Optimistic locking: an edit/archive/delete based on a stale screen fails instead of overwriting.
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Requirement() {}   // JPA needs this

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static Requirement create(Integer coordinatorId, String title, String description, LocalDate dueDate) {
        Requirement requirement = new Requirement();
        requirement.coordinatorId = coordinatorId;
        requirement.title = title;
        requirement.description = description;
        requirement.dueDate = dueDate;
        return requirement;
    }

    /** Title, description and due date can be edited, but only while the requirement is active. */
    public void edit(String title, String description, LocalDate dueDate) {
        requireActive();
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
    }

    public void archive(Instant now) {
        if (isArchived()) {
            throw new IllegalStateException("Already archived");
        }
        archivedAt = now;
    }

    /** Archived requirements are read-only: every change goes through this check. */
    public void requireActive() {
        if (isArchived()) {
            throw new IllegalStateException("This requirement is archived and can't be changed");
        }
    }

    public boolean isArchived() { return archivedAt != null; }
    public boolean isOwnedBy(Integer coordinatorId) { return this.coordinatorId.equals(coordinatorId); }

    public Integer getId() { return id; }
    public Integer getCoordinatorId() { return coordinatorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getArchivedAt() { return archivedAt; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
