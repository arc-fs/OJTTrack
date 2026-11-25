package com.ojttrack.backend.application;

import java.time.Instant;

/**
 * What the API sends back for an application. Keeping this separate from the
 * JobApplication entity means the JSON shape doesn't change when the table does,
 * and internal fields (like studentId) are never exposed by accident.
 */
public record ApplicationResponse(
        Integer id,
        String companyName,
        ApplicationStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(JobApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getCompanyName(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
