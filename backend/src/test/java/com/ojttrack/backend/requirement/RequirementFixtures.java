package com.ojttrack.backend.requirement;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds requirement entities with ids/versions for unit tests (normally set by the database). */
public final class RequirementFixtures {

    private RequirementFixtures() {}

    public static Requirement requirement(int id, int ownerId, String title, LocalDate dueDate) {
        Requirement requirement = Requirement.create(ownerId, title, null, dueDate);
        ReflectionTestUtils.setField(requirement, "id", id);
        ReflectionTestUtils.setField(requirement, "version", 0);
        return requirement;
    }

    public static Requirement archived(Requirement requirement) {
        ReflectionTestUtils.setField(requirement, "archivedAt", Instant.parse("2027-01-01T00:00:00Z"));
        return requirement;
    }

    public static RequirementAssignment assignment(int id, Requirement requirement, int studentId, AssignmentStatus status) {
        RequirementAssignment assignment = BeanAccess.newAssignment();
        ReflectionTestUtils.setField(assignment, "id", id);
        ReflectionTestUtils.setField(assignment, "requirementId", requirement.getId());
        ReflectionTestUtils.setField(assignment, "studentId", studentId);
        ReflectionTestUtils.setField(assignment, "status", status);
        ReflectionTestUtils.setField(assignment, "version", 0);
        if (status != AssignmentStatus.NOT_STARTED) {
            ReflectionTestUtils.setField(assignment, "submittedAt", Instant.parse("2027-01-05T00:00:00Z"));
        }
        if (status == AssignmentStatus.CONFIRMED) {
            ReflectionTestUtils.setField(assignment, "confirmedBy", requirement.getCoordinatorId());
            ReflectionTestUtils.setField(assignment, "confirmedAt", Instant.parse("2027-01-06T00:00:00Z"));
        }
        if (status == AssignmentStatus.RETURNED) {
            ReflectionTestUtils.setField(assignment, "feedback", "Please fix");
        }
        return assignment;
    }

    /** RequirementAssignment's no-arg constructor is protected (for JPA). */
    private static final class BeanAccess {
        static RequirementAssignment newAssignment() {
            return org.springframework.beans.BeanUtils.instantiateClass(RequirementAssignment.class);
        }
    }
}
