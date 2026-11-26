package com.ojttrack.backend.requirement;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A requirement as its owner sees it. progressCounts tells the UI how many students have
 * already submitted or been confirmed, so it can warn before an edit changes the meaning.
 */
public record RequirementResponse(
        Integer id,
        String title,
        String description,
        LocalDate dueDate,
        boolean archived,
        Instant archivedAt,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        ProgressCounts progressCounts) {

    public record ProgressCounts(int notStarted, int submitted, int returned, int confirmed) {}

    static RequirementResponse of(Requirement r, ProgressCounts counts) {
        return new RequirementResponse(r.getId(), r.getTitle(), r.getDescription(), r.getDueDate(), r.isArchived(),
                r.getArchivedAt(), r.getVersion(), r.getCreatedAt(), r.getUpdatedAt(), counts);
    }
}
