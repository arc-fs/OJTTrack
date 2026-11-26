package com.ojttrack.backend.dashboard;

import com.ojttrack.backend.application.ApplicationStatus;
import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.dashboard.CoordinatorDashboardResponse.StudentRow;
import com.ojttrack.backend.dashboard.CoordinatorDashboardResponse.Totals;
import com.ojttrack.backend.dashboard.StudentDashboardResponse.FollowUp;
import com.ojttrack.backend.dashboard.StudentDashboardResponse.PendingRequest;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementRequestRepository;
import com.ojttrack.backend.placement.PlacementRequestStatus;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.timelog.TimeLog;
import com.ojttrack.backend.timelog.TimeLogRepository;
import com.ojttrack.backend.timelog.TimeLogStatus;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds both dashboards from existing data, read-only. The coordinator dashboard loads
 * everything in a few bulk queries (students, their placements, those placements' logs)
 * instead of one query per student. The assistance rules live in AssistanceRules.
 */
@Service
public class DashboardService {

    /** Follow-ups for these statuses are still relevant; ACCEPTED/REJECTED/WITHDRAWN are settled. */
    static final Set<ApplicationStatus> OPEN_STATUSES = Set.of(
            ApplicationStatus.PLANNED, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW, ApplicationStatus.OFFERED);
    static final int FOLLOW_UP_WINDOW_DAYS = 7;

    private final JobApplicationRepository applications;
    private final PlacementRepository placements;
    private final PlacementRequestRepository requests;
    private final TimeLogRepository logs;
    private final AppUserRepository users;
    private final Clock clock;

    public DashboardService(JobApplicationRepository applications, PlacementRepository placements,
                            PlacementRequestRepository requests, TimeLogRepository logs,
                            AppUserRepository users, Clock clock) {
        this.applications = applications;
        this.placements = placements;
        this.requests = requests;
        this.logs = logs;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudentDashboardResponse studentDashboard(Integer studentId) {
        LocalDate today = LocalDate.now(clock);
        List<JobApplication> apps = applications.findByStudentId(studentId);
        Map<Integer, String> companies = apps.stream()
                .collect(Collectors.toMap(JobApplication::getId, JobApplication::getCompanyName));

        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            counts.put(status, 0L);
        }
        apps.forEach(app -> counts.merge(app.getStatus(), 1L, Long::sum));

        List<FollowUp> followUps = apps.stream()
                .filter(app -> OPEN_STATUSES.contains(app.getStatus()) && app.getFollowUpDate() != null)
                .filter(app -> !app.getFollowUpDate().isAfter(today.plusDays(FOLLOW_UP_WINDOW_DAYS)))
                .sorted(Comparator.comparing(JobApplication::getFollowUpDate))   // overdue (earliest) first
                .map(app -> new FollowUp(app.getId(), app.getCompanyName(), app.getStatus(),
                        app.getFollowUpDate(), app.getFollowUpDate().isBefore(today)))
                .toList();

        DashboardPlacement placement = AssistanceRules
                .currentPlacement(placements.findByStudentIdOrderByCreatedAtDesc(studentId))
                .map(p -> DashboardPlacement.of(p, companies.get(p.getApplicationId()),
                        logs.findByPlacementIdOrderByLogDateDesc(p.getId())))
                .orElse(null);

        PendingRequest pending = requests.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(r -> r.getStatus() == PlacementRequestStatus.PENDING)
                .findFirst()
                .map(r -> new PendingRequest(companies.get(r.getApplicationId()), r.isChange() ? "CHANGE" : "ACTIVATION"))
                .orElse(null);

        return new StudentDashboardResponse(counts, followUps, placement, pending);
    }

    @Transactional(readOnly = true)
    public CoordinatorDashboardResponse coordinatorDashboard(Integer coordinatorId) {
        LocalDate today = LocalDate.now(clock);
        List<AppUser> students = users.findByCoordinatorIdOrderByFullNameAsc(coordinatorId);
        if (students.isEmpty()) {
            return new CoordinatorDashboardResponse(new Totals(0, 0, 0, 0), List.of());
        }

        List<Placement> allPlacements = placements.findByStudentIdIn(
                students.stream().map(AppUser::getId).toList());
        Map<Integer, List<Placement>> placementsByStudent = allPlacements.stream()
                .collect(Collectors.groupingBy(Placement::getStudentId));

        // Logs are needed for ACTIVE placements (flags, queue count) and for the placement each
        // row shows (progress); for a student without an ACTIVE one that is the latest COMPLETED.
        Map<Integer, Placement> shownByStudent = students.stream()
                .flatMap(s -> AssistanceRules.currentPlacement(placementsByStudent.getOrDefault(s.getId(), List.of()))
                        .stream())
                .collect(Collectors.toMap(Placement::getStudentId, Function.identity()));
        List<TimeLog> relevantLogs = shownByStudent.isEmpty() ? List.of()
                : logs.findByPlacementIdIn(shownByStudent.values().stream().map(Placement::getId).toList());
        Map<Integer, List<TimeLog>> logsByPlacement = relevantLogs.stream()
                .collect(Collectors.groupingBy(TimeLog::getPlacementId));

        Map<Integer, String> companies = shownByStudent.isEmpty() ? Map.of()
                : applications.findAllById(shownByStudent.values().stream().map(Placement::getApplicationId).toList())
                        .stream().collect(Collectors.toMap(JobApplication::getId, JobApplication::getCompanyName));

        List<StudentRow> rows = students.stream()
                .map(student -> {
                    List<Placement> own = placementsByStudent.getOrDefault(student.getId(), List.of());
                    Placement shown = shownByStudent.get(student.getId());
                    List<TimeLog> shownLogs = shown == null ? List.of()
                            : logsByPlacement.getOrDefault(shown.getId(), List.of());
                    return new StudentRow(student.getId(), student.getFullName(), student.getEmail(),
                            student.getPlacementSearchStartsOn(),
                            shown == null ? null : DashboardPlacement.of(shown, companies.get(shown.getApplicationId()), shownLogs),
                            AssistanceRules.flagsFor(student.getPlacementSearchStartsOn(), own, shownLogs, today));
                })
                // flagged students first, then alphabetical (the repository already sorts by name)
                .sorted(Comparator.comparing((StudentRow row) -> row.flags().isEmpty()))
                .toList();

        long activePlacements = allPlacements.stream().filter(p -> p.getStatus() == PlacementStatus.ACTIVE).count();
        long completedPlacements = allPlacements.stream().filter(p -> p.getStatus() == PlacementStatus.COMPLETED).count();
        Set<Integer> activeIds = allPlacements.stream()
                .filter(p -> p.getStatus() == PlacementStatus.ACTIVE).map(Placement::getId).collect(Collectors.toSet());
        long awaitingReview = relevantLogs.stream()
                .filter(l -> l.getStatus() == TimeLogStatus.PENDING && activeIds.contains(l.getPlacementId()))
                .count();

        return new CoordinatorDashboardResponse(
                new Totals(students.size(), activePlacements, completedPlacements, awaitingReview), rows);
    }
}
