package com.ojttrack.backend.requirement;

import java.time.Instant;
import java.util.List;

/** One student's progress on a requirement, as the requirement's owner sees it. */
public record AssignmentResponse(
        Integer assignmentId,
        Integer studentId,
        String studentName,
        AssignmentStatus status,
        String note,
        String feedback,
        Instant submittedAt,
        String confirmedByName,
        Instant confirmedAt,
        Integer version,
        boolean overdue,
        List<EventResponse> history) {
}
