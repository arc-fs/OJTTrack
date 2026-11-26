package com.ojttrack.backend.requirement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A requirement as the student sees it: what to do, by when, their status, feedback and history. */
public record StudentRequirementResponse(
        Integer assignmentId,
        Integer requirementId,
        String title,
        String description,
        LocalDate dueDate,
        boolean archived,
        AssignmentStatus status,
        String note,
        String feedback,
        Instant submittedAt,
        Instant confirmedAt,
        Integer version,
        boolean overdue,
        boolean dueSoon,
        List<EventResponse> history) {
}
