package com.ojttrack.backend.requirement;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Coordinator requirement management (SecurityConfig: /api/coordinator/** is COORDINATOR only).
 * The service only allows requirements the logged-in coordinator owns.
 */
@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorRequirementController {

    private final RequirementService service;

    public CoordinatorRequirementController(RequirementService service) {
        this.service = service;
    }

    @GetMapping("/requirements")
    public List<RequirementResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                          @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.list(coordinator.getId(), includeArchived);
    }

    @PostMapping("/requirements")
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementResponse create(@Valid @RequestBody CreateRequirementRequest body,
                                      @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.create(coordinator.getId(), body);
    }

    @PutMapping("/requirements/{id}")
    public RequirementResponse update(@PathVariable Integer id, @Valid @RequestBody UpdateRequirementRequest body,
                                      @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.update(coordinator.getId(), id, body);
    }

    @PostMapping("/requirements/{id}/assign")
    public RequirementResponse assign(@PathVariable Integer id, @Valid @RequestBody AssignStudentsRequest body,
                                      @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.assign(coordinator.getId(), id, body);
    }

    @DeleteMapping("/requirements/{id}/students/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStudent(@PathVariable Integer id, @PathVariable Integer studentId,
                              @AuthenticationPrincipal AppUserDetails coordinator) {
        service.removeStudent(coordinator.getId(), id, studentId);
    }

    @PostMapping("/requirements/{id}/archive")
    public RequirementResponse archive(@PathVariable Integer id, @Valid @RequestBody VersionRequest body,
                                       @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.archive(coordinator.getId(), id, body.version());
    }

    /** version is a query parameter: DELETE requests conventionally have no body. */
    @DeleteMapping("/requirements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id, @RequestParam Integer version,
                       @AuthenticationPrincipal AppUserDetails coordinator) {
        service.delete(coordinator.getId(), id, version);
    }

    @GetMapping("/requirements/{id}/assignments")
    public List<AssignmentResponse> assignments(@PathVariable Integer id,
                                                @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.assignments(coordinator.getId(), id);
    }

    /** Completion review summary for one of my students (informational; never blocks completion). */
    @GetMapping("/students/{studentId}/requirements")
    public RequirementSummary studentSummary(@PathVariable Integer studentId,
                                             @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.studentSummary(coordinator.getId(), studentId);
    }
}
