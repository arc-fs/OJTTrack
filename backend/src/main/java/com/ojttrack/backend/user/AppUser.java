package com.ojttrack.backend.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // The coordinator who manages this student; null for coordinators (see V3 CHECK constraint).
    @Column(name = "coordinator_id")
    private Integer coordinatorId;

    // When this student is expected to start looking for a placement (V6). Set by the
    // coordinator; drives the dashboard's "No placement yet" flag. Null = not set.
    @Column(name = "placement_search_starts_on")
    private LocalDate placementSearchStartsOn;

    protected AppUser() {}   // JPA needs this

    private AppUser(String email, String passwordHash, String fullName, Role role, Integer coordinatorId) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.coordinatorId = coordinatorId;
    }

    // Named factories instead of a public constructor: a student can't be built without
    // a coordinator, and a coordinator can't be given one, matching the database rule.
    public static AppUser coordinator(String email, String passwordHash, String fullName) {
        return new AppUser(email, passwordHash, fullName, Role.COORDINATOR, null);
    }

    public static AppUser student(String email, String passwordHash, String fullName, Integer coordinatorId) {
        if (coordinatorId == null) {
            throw new IllegalArgumentException("A student must have a coordinator");
        }
        return new AppUser(email, passwordHash, fullName, Role.STUDENT, coordinatorId);
    }

    /** Only students have this date (mirrors the V6 CHECK constraint). Null clears it. */
    public void setPlacementSearchStartsOn(LocalDate date) {
        if (role != Role.STUDENT && date != null) {
            throw new IllegalStateException("Only students have a placement search start date");
        }
        this.placementSearchStartsOn = date;
    }

    public LocalDate getPlacementSearchStartsOn() { return placementSearchStartsOn; }
    public Integer getId() { return id; }
    public Integer getCoordinatorId() { return coordinatorId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}