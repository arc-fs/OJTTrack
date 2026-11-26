package com.ojttrack.backend.placement;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Coordinator endpoints (SecurityConfig: /api/coordinator/** is COORDINATOR only).
 * The service only lets a coordinator see and decide requests from their own students.
 */
@RestController
@RequestMapping("/api/coordinator/placement-requests")
public class CoordinatorPlacementController {

    private final PlacementService service;

    public CoordinatorPlacementController(PlacementService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlacementRequestResponse> pending(@AuthenticationPrincipal AppUserDetails coordinator) {
        return service.pendingRequestsFor(coordinator.getId());
    }

    @PostMapping("/{id}/approve")
    public PlacementRequestResponse approve(@PathVariable Integer id,
                                            @Valid @RequestBody ApprovePlacementRequest body,
                                            @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.approve(coordinator.getId(), id,
                body.startDate(), body.endDate(), body.requiredHours(), body.role(), body.note());
    }

    @PostMapping("/{id}/reject")
    public PlacementRequestResponse reject(@PathVariable Integer id,
                                           @Valid @RequestBody RejectPlacementRequest body,
                                           @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.reject(coordinator.getId(), id, body.note());
    }
}
