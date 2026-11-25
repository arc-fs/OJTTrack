package com.ojttrack.backend.placement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /api/placement-requests. */
public record SubmitPlacementRequest(
        @NotNull Integer applicationId,
        @Size(max = 500) String reason) {          // placement_requests.reason VARCHAR(500)
}
