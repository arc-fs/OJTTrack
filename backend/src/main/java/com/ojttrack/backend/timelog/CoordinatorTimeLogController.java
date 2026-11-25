package com.ojttrack.backend.timelog;

import com.ojttrack.backend.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Coordinator time log endpoints (SecurityConfig: /api/coordinator/** is COORDINATOR only).
 * The service only allows the coordinator's own students' placements and logs.
 */
@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorTimeLogController {

    private final TimeLogReviewService service;

    public CoordinatorTimeLogController(TimeLogReviewService service) {
        this.service = service;
    }

    @GetMapping("/time-logs")
    public List<ReviewQueueItem> pendingQueue(@AuthenticationPrincipal AppUserDetails coordinator) {
        return service.pendingQueue(coordinator.getId());
    }

    @PostMapping("/time-logs/{id}/verify")
    public TimeLogResponse verify(@PathVariable Integer id, @Valid @RequestBody ReviewTimeLogRequest body,
                                  @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.verify(coordinator.getId(), id, body.version());
    }

    @PostMapping("/time-logs/{id}/return")
    public TimeLogResponse returnLog(@PathVariable Integer id, @Valid @RequestBody ReturnTimeLogRequest body,
                                     @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.returnLog(coordinator.getId(), id, body.version(), body.feedback());
    }

    @PostMapping("/time-logs/{id}/unverify")
    public TimeLogResponse unverify(@PathVariable Integer id, @Valid @RequestBody UnverifyTimeLogRequest body,
                                    @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.unverify(coordinator.getId(), id, body.version(), body.reason());
    }

    @GetMapping("/placements")
    public List<CoordinatorPlacementResponse> activePlacements(@AuthenticationPrincipal AppUserDetails coordinator) {
        return service.activePlacements(coordinator.getId());
    }

    @GetMapping("/placements/{id}/time-logs")
    public List<TimeLogResponse> placementLogs(@PathVariable Integer id,
                                               @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.placementLogs(coordinator.getId(), id);
    }

    @GetMapping("/placements/{id}/summary")
    public TimeLogSummaryResponse summary(@PathVariable Integer id,
                                          @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.summary(coordinator.getId(), id);
    }

    @PostMapping("/placements/{id}/complete")
    public TimeLogSummaryResponse complete(@PathVariable Integer id,
                                           @AuthenticationPrincipal AppUserDetails coordinator) {
        return service.complete(coordinator.getId(), id);
    }
}
