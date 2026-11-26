package com.ojttrack.backend.timelog;

import com.ojttrack.backend.application.JobApplication;
import com.ojttrack.backend.application.JobApplicationRepository;
import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordinator side of time logs: review queue, verify / return / un-verify, progress,
 * and confirming completion. Each public method is one transaction.
 *
 * Every change locks the placement row first (like log submission and transfer
 * approval), then checks, in order: the log/placement belongs to one of this
 * coordinator's students (404 otherwise), the placement is ACTIVE, the version matches
 * what the coordinator saw, and the log is in the right state (409 otherwise).
 *
 * Completion lives here rather than in PlacementService because it depends on time
 * logs; this keeps package dependencies one-way (timelog -> placement).
 */
@Service
public class TimeLogReviewService {

    private final TimeLogRepository logs;
    private final PlacementRepository placements;
    private final JobApplicationRepository applications;
    private final AppUserRepository users;
    private final Clock clock;

    public TimeLogReviewService(TimeLogRepository logs, PlacementRepository placements,
                                JobApplicationRepository applications, AppUserRepository users, Clock clock) {
        this.logs = logs;
        this.placements = placements;
        this.applications = applications;
        this.users = users;
        this.clock = clock;
    }

    // ---- reading ----

    /** PENDING logs from this coordinator's students' ACTIVE placements, oldest first. */
    @Transactional(readOnly = true)
    public List<ReviewQueueItem> pendingQueue(Integer coordinatorId) {
        List<TimeLog> pending = logs.findForCoordinator(coordinatorId, TimeLogStatus.PENDING, PlacementStatus.ACTIVE);
        Map<Integer, Placement> placementById = placements.findAllById(
                        pending.stream().map(TimeLog::getPlacementId).distinct().toList())
                .stream().collect(Collectors.toMap(Placement::getId, p -> p));
        Map<Integer, String> companies = companyNames(placementById.values().stream().toList());
        Map<Integer, String> students = names(pending.stream().map(TimeLog::getStudentId).toList());

        return pending.stream()
                .map(log -> new ReviewQueueItem(
                        TimeLogResponse.of(log, null),
                        students.get(log.getStudentId()),
                        companies.get(placementById.get(log.getPlacementId()).getApplicationId())))
                .toList();
    }

    /** Active placements of this coordinator's students, each with its progress. */
    @Transactional(readOnly = true)
    public List<CoordinatorPlacementResponse> activePlacements(Integer coordinatorId) {
        List<Placement> active = placements.findForCoordinator(coordinatorId, PlacementStatus.ACTIVE);
        Map<Integer, String> companies = companyNames(active);
        Map<Integer, String> students = names(active.stream().map(Placement::getStudentId).toList());
        // shortcut: one log query per placement; fine for one coordinator's students, batch if it gets slow
        return active.stream()
                .map(p -> new CoordinatorPlacementResponse(
                        p.getId(), p.getStudentId(), students.get(p.getStudentId()), companies.get(p.getApplicationId()),
                        p.getStartDate(), p.getEndDate(), p.getRequiredHours(),
                        TimeLogSummaryResponse.of(p, logs.findByPlacementIdOrderByLogDateDesc(p.getId()))))
                .toList();
    }

    /** All logs of one of this coordinator's students' placements (ended ones too, as history). */
    @Transactional(readOnly = true)
    public List<TimeLogResponse> placementLogs(Integer coordinatorId, Integer placementId) {
        Placement placement = ownPlacement(coordinatorId, placementId);
        List<TimeLog> found = logs.findByPlacementIdOrderByLogDateDesc(placement.getId());
        Map<Integer, String> verifiers = names(found.stream().map(TimeLog::getVerifiedBy).filter(Objects::nonNull).toList());
        return found.stream().map(log -> TimeLogResponse.of(log, verifiers.get(log.getVerifiedBy()))).toList();
    }

    @Transactional(readOnly = true)
    public TimeLogSummaryResponse summary(Integer coordinatorId, Integer placementId) {
        Placement placement = ownPlacement(coordinatorId, placementId);
        return TimeLogSummaryResponse.of(placement, logs.findByPlacementIdOrderByLogDateDesc(placement.getId()));
    }

    // ---- review ----

    @Transactional
    public TimeLogResponse verify(Integer coordinatorId, Integer logId, Integer version) {
        TimeLog log = lockForReview(coordinatorId, logId, version, TimeLogStatus.PENDING, "Only pending logs can be verified");
        log.verify(coordinatorId, Instant.now(clock));
        return TimeLogResponse.of(logs.saveAndFlush(log), nameOf(coordinatorId));
    }

    @Transactional
    public TimeLogResponse returnLog(Integer coordinatorId, Integer logId, Integer version, String feedback) {
        TimeLog log = lockForReview(coordinatorId, logId, version, TimeLogStatus.PENDING, "Only pending logs can be returned");
        log.returnWithFeedback(feedback);
        return TimeLogResponse.of(logs.saveAndFlush(log), null);
    }

    /** Undo a mistaken verification: VERIFIED -> RETURNED with the reason as feedback. */
    @Transactional
    public TimeLogResponse unverify(Integer coordinatorId, Integer logId, Integer version, String reason) {
        TimeLog log = lockForReview(coordinatorId, logId, version, TimeLogStatus.VERIFIED, "Only verified logs can be un-verified");
        log.unverify(reason);
        return TimeLogResponse.of(logs.saveAndFlush(log), null);
    }

    // ---- completion ----

    /**
     * The coordinator confirms the placement is complete. Never automatic. Requires the
     * verified hours to reach the requirement and no PENDING logs left unreviewed.
     * The placement lock means no log can be submitted or verified meanwhile.
     */
    @Transactional
    public TimeLogSummaryResponse complete(Integer coordinatorId, Integer placementId) {
        Placement placement = placements.findByIdForUpdate(placementId)
                .filter(p -> isOwnStudent(coordinatorId, p.getStudentId()))
                .orElseThrow(() -> notFound("Placement not found"));
        if (placement.getStatus() != PlacementStatus.ACTIVE) {
            throw conflict("Only an active placement can be completed");
        }
        List<TimeLog> placementLogs = logs.findByPlacementIdOrderByLogDateDesc(placement.getId());
        TimeLogSummaryResponse progress = TimeLogSummaryResponse.of(placement, placementLogs);
        if (progress.pendingCount() > 0) {
            throw conflict(progress.pendingCount() + " log(s) are still pending. Verify or return them before completing");
        }
        if (!progress.requirementReached()) {
            throw conflict("The required hours have not been reached yet");
        }
        placement.complete(coordinatorId);
        placements.saveAndFlush(placement);
        return TimeLogSummaryResponse.of(placement, placementLogs);
    }

    // ---- helpers ----

    private TimeLog lockForReview(Integer coordinatorId, Integer logId, Integer version,
                                  TimeLogStatus requiredStatus, String wrongStatusMessage) {
        TimeLog log = logs.findById(logId).orElseThrow(() -> notFound("Time log not found"));
        Placement placement = placements.findByIdForUpdate(log.getPlacementId())
                .orElseThrow(() -> notFound("Time log not found"));
        if (!isOwnStudent(coordinatorId, placement.getStudentId())) {
            throw notFound("Time log not found");   // same as "doesn't exist": reveal nothing
        }
        if (placement.getStatus() != PlacementStatus.ACTIVE) {
            throw conflict("This placement has ended, so its logs can no longer be reviewed");
        }
        if (!Objects.equals(log.getVersion(), version)) {
            throw conflict("This log changed since you loaded it. Reload and review it again");
        }
        if (log.getStatus() != requiredStatus) {
            throw conflict(wrongStatusMessage);
        }
        return log;
    }

    private Placement ownPlacement(Integer coordinatorId, Integer placementId) {
        return placements.findById(placementId)
                .filter(p -> isOwnStudent(coordinatorId, p.getStudentId()))
                .orElseThrow(() -> notFound("Placement not found"));
    }

    private boolean isOwnStudent(Integer coordinatorId, Integer studentId) {
        return users.findById(studentId).map(s -> coordinatorId.equals(s.getCoordinatorId())).orElse(false);
    }

    private Map<Integer, String> names(List<Integer> userIds) {
        return users.findAllById(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
    }

    private String nameOf(Integer userId) {
        return users.findById(userId).map(AppUser::getFullName).orElse(null);
    }

    private Map<Integer, String> companyNames(List<Placement> list) {
        return applications.findAllById(list.stream().map(Placement::getApplicationId).distinct().toList()).stream()
                .collect(Collectors.toMap(JobApplication::getId, JobApplication::getCompanyName));
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
