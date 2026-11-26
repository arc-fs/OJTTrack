package com.ojttrack.backend.coordinator;

import java.time.LocalDate;

/**
 * Body of PUT /api/coordinator/students/{id}/placement-search-start.
 * date = null clears it (the student is then never flagged "No placement yet").
 */
public record PlacementSearchStartRequest(LocalDate date) {
}
