package com.ojttrack.backend.requirement;

import jakarta.validation.constraints.NotNull;

/** A body that only carries the version the user saw (archive, confirm). */
public record VersionRequest(@NotNull Integer version) {
}
