package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Body of POST /api/coordinator/requirements. studentIds null or empty = all students
 * CURRENTLY assigned to the coordinator (future students don't inherit it).
 */
public record CreateRequirementRequest(
        @NotBlank @Size(max = 200) String title,            // requirements.title VARCHAR(200)
        @Size(max = 2000) String description,               // requirements.description VARCHAR(2000)
        LocalDate dueDate,                                  // optional
        List<Integer> studentIds) {

    public CreateRequirementRequest {
        title = title == null ? null : title.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
