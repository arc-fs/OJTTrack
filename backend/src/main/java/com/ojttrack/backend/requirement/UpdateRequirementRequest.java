package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of PUT /api/coordinator/requirements/{id}; version = the one the coordinator saw. */
public record UpdateRequirementRequest(
        @NotNull Integer version,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        LocalDate dueDate) {

    public UpdateRequirementRequest {
        title = title == null ? null : title.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
