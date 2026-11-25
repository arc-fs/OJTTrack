package com.ojttrack.backend.timelog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** A time log as the API returns it. Minutes are whole numbers; the UI formats them. */
public record TimeLogResponse(
        Integer id,
        Integer placementId,
        LocalDate logDate,
        LocalTime timeIn,
        LocalTime timeOut,
        boolean endsNextDay,
        Integer breakMinutes,
        Integer workedMinutes,
        String accomplishments,
        TimeLogStatus status,
        String feedback,
        String verifiedByName,
        Instant verifiedAt,
        Integer version,
        Instant createdAt,
        Instant updatedAt) {

    static TimeLogResponse of(TimeLog log, String verifiedByName) {
        return new TimeLogResponse(
                log.getId(), log.getPlacementId(), log.getLogDate(), log.getTimeIn(), log.getTimeOut(),
                log.isEndsNextDay(), log.getBreakMinutes(), log.getWorkedMinutes(), log.getAccomplishments(),
                log.getStatus(), log.getFeedback(), verifiedByName, log.getVerifiedAt(), log.getVersion(),
                log.getCreatedAt(), log.getUpdatedAt());
    }
}
