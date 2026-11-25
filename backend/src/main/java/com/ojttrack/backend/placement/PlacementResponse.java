package com.ojttrack.backend.placement;

import java.time.Instant;
import java.time.LocalDate;

public record PlacementResponse(
        Integer id,
        Integer applicationId,
        String companyName,
        PlacementStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Integer requiredHours,
        Instant createdAt,
        Instant endedAt) {

    static PlacementResponse of(Placement placement, String companyName) {
        return new PlacementResponse(
                placement.getId(), placement.getApplicationId(), companyName, placement.getStatus(),
                placement.getStartDate(), placement.getEndDate(), placement.getRequiredHours(),
                placement.getCreatedAt(), placement.getEndedAt());
    }
}
