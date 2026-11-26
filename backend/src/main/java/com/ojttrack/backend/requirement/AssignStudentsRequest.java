package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body of POST /api/coordinator/requirements/{id}/assign: more of the coordinator's students. */
public record AssignStudentsRequest(@NotEmpty List<@NotNull Integer> studentIds) {
}
