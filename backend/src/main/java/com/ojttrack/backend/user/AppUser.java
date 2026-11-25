package com.ojttrack.backend.user;

import jakarta.persistence.*;
import java.time.Instant;

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

    public Integer getId() { return id; }
    public Integer getCoordinatorId() { return coordinatorId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}