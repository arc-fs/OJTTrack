package com.ojttrack.backend.timelog;

import com.ojttrack.backend.placement.Placement;
import com.ojttrack.backend.placement.PlacementRepository;
import com.ojttrack.backend.placement.PlacementStatus;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Student-side time log rules. Each public method is one transaction.
 *
 * Concurrency: every write locks the placement row first (the same lock a transfer
 * approval takes), so a log can't slip in while the placement is being terminated.
 * Edits also compare the log's version with the one the student saw, so a stale
 * screen can't overwrite newer data.
 *
 * Status codes: 404 = not found / not yours, 409 = conflicts with the current state,
 * 400 = invalid input.
 */
@Service
public class TimeLogService {

    private final TimeLogRepository logs;
    private final PlacementRepository placements;
    private final AppUserRepository users;
    private final Clock clock;

    public TimeLogService(TimeLogRepository logs, PlacementRepository placements,
                          AppUserRepository users, Clock clock) {
        this.logs = logs;
        this.placements = placements;
        this.users = users;
        this.clock = clock;
    }

    /** A new log always goes to the student's ACTIVE placement (never one chosen by the client). */
    @Transactional
    public TimeLogResponse submit(Integer studentId, TimeLogRequest request) {
        Placement placement = placements.findForUpdateByStudentIdAndStatus(studentId, PlacementStatus.ACTIVE)
                .orElseThrow(() -> conflict("You don't have an active placement to log hours for"));
        validate(request, placement);
        if (logs.existsByPlacementIdAndLogDate(placement.getId(), request.logDate())) {
            throw conflict("You already have a log for " + request.logDate()
                    + ". For a split shift, edit that log and put the gap in Break");
        }
        TimeLog log = TimeLog.submit(placement.getId(), studentId, request.logDate(),
                request.timeIn(), request.timeOut(), request.endsNextDay(),
                request.breakMinutes(), request.accomplishments().trim());
        // Flush now so PostgreSQL calculates worked_minutes and Hibernate reads it back.
        return TimeLogResponse.of(logs.saveAndFlush(log), null);
    }

    /** Correct a PENDING or RETURNED log; a RETURNED one is resubmitted as PENDING. */
    @Transactional
    public TimeLogResponse edit(Integer studentId, Integer logId, TimeLogRequest request) {
        if (request.version() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version is required when editing a log");
        }
        TimeLog log = logs.findByIdAndStudentId(logId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Time log not found"));
        Placement placement = placements.findByIdForUpdate(log.getPlacementId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Time log not found"));

        if (placement.getStatus() != PlacementStatus.ACTIVE) {
            throw conflict("This placement has ended, so its logs can no longer be changed");
        }
        if (!Objects.equals(log.getVersion(), request.version())) {
            throw conflict("This log changed since you loaded it. Reload and try again");
        }
        if (log.getStatus() == TimeLogStatus.VERIFIED) {
            throw conflict("Verified logs can't be edited");
        }
        validate(request, placement);
        if (logs.existsByPlacementIdAndLogDateAndIdNot(placement.getId(), request.logDate(), log.getId())) {
            throw conflict("You already have a log for " + request.logDate());
        }

        log.edit(request.logDate(), request.timeIn(), request.timeOut(), request.endsNextDay(),
                request.breakMinutes(), request.accomplishments().trim());
        return TimeLogResponse.of(logs.saveAndFlush(log), null);
    }

    /**
     * Logs of one of the student's placements, newest first. Without placementId:
     * the ACTIVE placement (empty list if there is none). Old placements stay readable.
     */
    @Transactional(readOnly = true)
    public List<TimeLogResponse> list(Integer studentId, Integer placementId) {
        Integer id;
        if (placementId == null) {
            id = placements.findByStudentIdAndStatus(studentId, PlacementStatus.ACTIVE)
                    .map(Placement::getId)
                    .orElse(null);
            if (id == null) {
                return List.of();
            }
        } else {
            id = placements.findById(placementId)
                    .filter(p -> p.getStudentId().equals(studentId))
                    .map(Placement::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement not found"));
        }

        List<TimeLog> found = logs.findByPlacementIdOrderByLogDateDesc(id);
        List<Integer> verifierIds = found.stream().map(TimeLog::getVerifiedBy).filter(Objects::nonNull).distinct().toList();
        Map<Integer, String> verifierNames = users.findAllById(verifierIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return found.stream()
                .map(log -> TimeLogResponse.of(log, verifierNames.get(log.getVerifiedBy())))
                .toList();
    }

    /** Shift rules (shared with the database CHECKs) plus the date rules that need the clock. */
    private void validate(TimeLogRequest request, Placement placement) {
        String problem = TimeLog.shiftProblem(request.timeIn(), request.timeOut(),
                request.endsNextDay(), request.breakMinutes());
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
        LocalDate today = LocalDate.now(clock);   // Asia/Manila, see TimeConfig
        if (request.logDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't log a future date");
        }
        if (request.logDate().isBefore(placement.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The date is before your placement starts (" + placement.getStartDate() + ")");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
