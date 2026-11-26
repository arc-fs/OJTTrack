package com.ojttrack.backend.dashboard;

import com.ojttrack.backend.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard endpoints. SecurityConfig: /api/dashboard is STUDENT only, and
 * /api/coordinator/** is COORDINATOR only. The user always comes from the session.
 */
@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard")
    public StudentDashboardResponse studentDashboard(@AuthenticationPrincipal AppUserDetails student) {
        return service.studentDashboard(student.getId());
    }

    @GetMapping("/api/coordinator/dashboard")
    public CoordinatorDashboardResponse coordinatorDashboard(@AuthenticationPrincipal AppUserDetails coordinator) {
        return service.coordinatorDashboard(coordinator.getId());
    }
}
