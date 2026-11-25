package com.ojttrack.backend.placement;

import jakarta.validation.constraints.Size;

/** Body of POST /api/coordinator/placement-requests/{id}/reject. */
public record RejectPlacementRequest(
        @Size(max = 500) String note) {            // placement_requests.decision_note VARCHAR(500)
}
