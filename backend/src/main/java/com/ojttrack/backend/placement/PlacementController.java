package com.ojttrack.backend.placement;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Student endpoints (SecurityConfig: STUDENT only). The student id always comes from the session. */
@RestController
@RequestMapping("/api")
public class PlacementController {

    private final PlacementService service;

    public PlacementController(PlacementService service) {
        this.service = service;
    }

    @GetMapping("/placements")
    public List<PlacementResponse> myPlacements(@AuthenticationPrincipal AppUserDetails student) {
        return service.placementsFor(student.getId());
    }

    @GetMapping("/placement-requests")
    public List<PlacementRequestResponse> myRequests(@AuthenticationPrincipal AppUserDetails student) {
        return service.requestsFor(student.getId());
    }

    @PostMapping("/placement-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public PlacementRequestResponse submit(@Valid @RequestBody SubmitPlacementRequest body,
                                           @AuthenticationPrincipal AppUserDetails student) {
        return service.submit(student.getId(), body.applicationId(), body.reason());
    }
}
