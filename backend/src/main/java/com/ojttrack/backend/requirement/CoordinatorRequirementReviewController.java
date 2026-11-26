package com.ojttrack.backend.requirement;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Coordinator review of submissions. Only the requirement's owner may review (service). */
@RestController
@RequestMapping("/api/coordinator/requirement-assignments")
public class CoordinatorRequirementReviewController {

    private final RequirementReviewService service;

    public CoordinatorRequirementReviewController(RequirementReviewService service) {
        this.service = service;
    }

    @PostMapping("/{id}/confirm")
    public AssignmentResponse confirm(@PathVariable Integer id, @Valid @RequestBody VersionRequest body,
                                      @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.confirm(coordinator.getId(), id, body.version());
    }

    @PostMapping("/{id}/return")
    public AssignmentResponse returnWithFeedback(@PathVariable Integer id, @Valid @RequestBody ReturnRequirementRequest body,
                                                 @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.returnWithFeedback(coordinator.getId(), id, body.version(), body.feedback());
    }

    @PostMapping("/{id}/unconfirm")
    public AssignmentResponse unconfirm(@PathVariable Integer id, @Valid @RequestBody UnconfirmRequirementRequest body,
                                        @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.unconfirm(coordinator.getId(), id, body.version(), body.reason());
    }
}
