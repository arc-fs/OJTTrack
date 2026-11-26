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
import com.ojttrack.backend.dashboard.StudentDashboardResponse.RequirementItem;
import com.ojttrack.backend.requirement.AssignmentStatus;
import com.ojttrack.backend.requirement.Requirement;
import com.ojttrack.backend.requirement.RequirementAssignment;
import com.ojttrack.backend.requirement.RequirementAssignmentRepository;
import com.ojttrack.backend.requirement.RequirementRepository;
import com.ojttrack.backend.requirement.RequirementRules;
import com.ojttrack.backend.requirement.RequirementSummary;
import com.ojttrack.backend.requirement.RequirementSummary.OverdueItem;
import java.util.ArrayList;
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
    private final RequirementRepository requirements;
    private final RequirementAssignmentRepository requirementAssignments;
    private final Clock clock;

    public DashboardService(JobApplicationRepository applications, PlacementRepository placements,
                            PlacementRequestRepository requests, TimeLogRepository logs,
                            AppUserRepository users, RequirementRepository requirements,
                            RequirementAssignmentRepository requirementAssignments, Clock clock) {
        this.applications = applications;
        this.placements = placements;
        this.requests = requests;
        this.logs = logs;
        this.users = users;
        this.requirements = requirements;
        this.requirementAssignments = requirementAssignments;
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

        return new StudentDashboardResponse(counts, followUps, placement, pending, studentRequirements(studentId, today));
    }

    /** All of the student's non-archived requirements that need their attention, whoever owns them. */
    private StudentDashboardResponse.Requirements studentRequirements(Integer studentId, LocalDate today) {
        List<RequirementAssignment> active = requirementAssignments.findActiveByStudentId(studentId);
        Map<Integer, Requirement> byId = active.isEmpty() ? Map.of()
                : requirements.findAllById(active.stream().map(RequirementAssignment::getRequirementId).toList())
                        .stream().collect(Collectors.toMap(Requirement::getId, Function.identity()));
        List<RequirementItem> overdue = new ArrayList<>();
        List<RequirementItem> dueSoon = new ArrayList<>();
        List<RequirementItem> returned = new ArrayList<>();
        for (RequirementAssignment a : active) {
            Requirement r = byId.get(a.getRequirementId());
            if (r == null || r.isArchived()) {
                continue;
            }
            RequirementItem item = new RequirementItem(a.getId(), r.getTitle(), r.getDueDate(), a.getStatus(), a.getFeedback());
            if (RequirementRules.isOverdue(a.getStatus(), r.getDueDate(), false, today)) {
                overdue.add(item);
            } else if (RequirementRules.isDueSoon(a.getStatus(), r.getDueDate(), false, today)) {
                dueSoon.add(item);
            }
            if (a.getStatus() == AssignmentStatus.RETURNED) {
                returned.add(item);
            }
        }
        Comparator<RequirementItem> byDate = Comparator.comparing(RequirementItem::dueDate,
                Comparator.nullsLast(Comparator.naturalOrder()));
        overdue.sort(byDate);
        dueSoon.sort(byDate);
        returned.sort(byDate);
        return new StudentDashboardResponse.Requirements(overdue, dueSoon, returned);
    }

    @Transactional(readOnly = true)
    public CoordinatorDashboardResponse coordinatorDashboard(Integer coordinatorId) {
        LocalDate today = LocalDate.now(clock);
        // Requirements this coordinator OWNS that wait for their review (any student, even one
        // since reassigned): exactly what their Requirements tab lets them review.
        long requirementsAwaitingReview = requirementAssignments.countActiveSubmittedForCoordinator(coordinatorId);
        List<AppUser> students = users.findByCoordinatorIdOrderByFullNameAsc(coordinatorId);
        if (students.isEmpty()) {
            return new CoordinatorDashboardResponse(new Totals(0, 0, 0, 0, requirementsAwaitingReview), List.of());
        }

        // Overdue items per CURRENT student, from non-archived requirements this coordinator owns.
        Map<Integer, Requirement> ownedActive = requirements.findByCoordinatorIdAndArchivedAtIsNull(coordinatorId)
                .stream().collect(Collectors.toMap(Requirement::getId, Function.identity()));
        Map<Integer, List<RequirementAssignment>> assignmentsByStudent = ownedActive.isEmpty() ? Map.of()
                : requirementAssignments.findActiveForCoordinator(coordinatorId,
                                students.stream().map(AppUser::getId).toList())
                        .stream().collect(Collectors.groupingBy(RequirementAssignment::getStudentId));

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
                    List<OverdueItem> overdue = RequirementSummary.of(
                            assignmentsByStudent.getOrDefault(student.getId(), List.of()), ownedActive, today).overdue();
                    return new StudentRow(student.getId(), student.getFullName(), student.getEmail(),
                            student.getPlacementSearchStartsOn(),
                            shown == null ? null : DashboardPlacement.of(shown, companies.get(shown.getApplicationId()), shownLogs),
                            AssistanceRules.flagsFor(student.getPlacementSearchStartsOn(), own, shownLogs, overdue, today),
                            overdue);
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
                new Totals(students.size(), activePlacements, completedPlacements, awaitingReview,
                        requirementsAwaitingReview), rows);
    }
}
