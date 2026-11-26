package com.ojttrack.backend.requirement;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One step in an assignment's history. Immutable: created once, never updated or deleted
 * (no setters, every column updatable = false, and the RESTRICT foreign key in V7 means the
 * database refuses to cascade-delete history). Keeps its own copy of the note or reason.
 */
@Entity
@Table(name = "requirement_events")
public class RequirementEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "assignment_id", nullable = false, updatable = false)
    private Integer assignmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EventAction action;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Integer actorId;

    @Column(updatable = false)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RequirementEvent() {}   // JPA needs this

    public static RequirementEvent record(RequirementAssignment assignment, EventAction action,
                                          Integer actorId, String text, Instant now) {
        RequirementEvent event = new RequirementEvent();
        event.assignmentId = assignment.getId();
        event.action = action;
        event.actorId = actorId;
        event.text = text;
        event.createdAt = now;
        return event;
    }

    public Integer getId() { return id; }
    public Integer getAssignmentId() { return assignmentId; }
    public EventAction getAction() { return action; }
    public Integer getActorId() { return actorId; }
    public String getText() { return text; }
    public Instant getCreatedAt() { return createdAt; }
}
