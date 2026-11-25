package com.ojttrack.backend.placement;

import java.time.Instant;

/** type is ACTIVATION (student had no placement) or CHANGE (would replace the current one). */
public record PlacementRequestResponse(
        Integer id,
        Integer applicationId,
        String companyName,
        String studentName,
        String type,
        PlacementRequestStatus status,
        String reason,
        String decisionNote,
        Instant createdAt,
        Instant decidedAt) {

    static PlacementRequestResponse of(PlacementRequest request, String companyName, String studentName) {
        return new PlacementRequestResponse(
                request.getId(), request.getApplicationId(), companyName, studentName,
                request.isChange() ? "CHANGE" : "ACTIVATION", request.getStatus(),
                request.getReason(), request.getDecisionNote(), request.getCreatedAt(), request.getDecidedAt());
    }
}
