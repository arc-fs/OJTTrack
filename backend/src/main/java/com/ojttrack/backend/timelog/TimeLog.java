package com.ojttrack.backend.timelog;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One day of OJT work for one placement. log_date is the day the shift starts;
 * endsNextDay marks an overnight shift (e.g. 22:00 -> 06:00).
 * workedMinutes is calculated by PostgreSQL (see V5), never by Java.
 */
@Entity
@Table(name = "time_logs")
public class TimeLog {

    /** Longest allowed shift, time in to time out including breaks (V5 chk_time_logs_max_shift). */
    public static final int MAX_SHIFT_MINUTES = 960;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "placement_id", nullable = false, updatable = false)
    private Integer placementId;

    // Same value as the placement's student; the composite FK in V5 keeps them equal.
    // Stored so every ownership check is a simple "id AND student_id" lookup.
    @Column(name = "student_id", nullable = false, updatable = false)
    private Integer studentId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "time_in", nullable = false)
    private LocalTime timeIn;

    @Column(name = "time_out", nullable = false)
    private LocalTime timeOut;

    @Column(name = "ends_next_day", nullable = false)
    private boolean endsNextDay;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes;

    @Column(nullable = false)
    private String accomplishments;

    // Database-generated: Hibernate never writes it and re-reads it after INSERT and UPDATE.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "worked_minutes", insertable = false, updatable = false)
    private Integer workedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeLogStatus status;

    private String feedback;

    @Column(name = "verified_by")
    private Integer verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    // Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and
    // increments it, so a write based on stale data fails instead of overwriting.
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TimeLog() {}   // JPA needs this

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Checks the shift rules shared by the API and the database (V5 CHECKs).
     * Returns a message for the user, or null when the shift is valid.
     */
    public static String shiftProblem(LocalTime timeIn, LocalTime timeOut, boolean endsNextDay, int breakMinutes) {
        if (timeIn.equals(timeOut)) {
            return "Time in and time out can't be the same";
        }
        boolean outIsEarlier = timeOut.isBefore(timeIn);
        if (outIsEarlier && !endsNextDay) {
            return "Time out is earlier than time in. If this shift ends the next day, tick 'Ends next day'; "
                    + "otherwise correct the times";
        }
        if (!outIsEarlier && endsNextDay) {
            return "'Ends next day' is ticked, but time out is later than time in on the same day";
        }
        long shift = shiftMinutes(timeIn, timeOut, endsNextDay);
        if (shift > MAX_SHIFT_MINUTES) {
            return "A shift can't be longer than 16 hours";
        }
        if (breakMinutes < 0) {
            return "Break can't be negative";
        }
        if (breakMinutes >= shift) {
            return "Break must be shorter than the shift";
        }
        return null;
    }

    /** Minutes from time in to time out, breaks included. */
    public static long shiftMinutes(LocalTime timeIn, LocalTime timeOut, boolean endsNextDay) {
        long minutes = Duration.between(timeIn, timeOut).toMinutes();
        return endsNextDay ? minutes + 24 * 60 : minutes;
    }

    public static TimeLog submit(Integer placementId, Integer studentId, LocalDate logDate,
                                 LocalTime timeIn, LocalTime timeOut, boolean endsNextDay,
                                 int breakMinutes, String accomplishments) {
        requireValidShift(timeIn, timeOut, endsNextDay, breakMinutes);
        TimeLog log = new TimeLog();
        log.placementId = placementId;
        log.studentId = studentId;
        log.logDate = logDate;
        log.timeIn = timeIn;
        log.timeOut = timeOut;
        log.endsNextDay = endsNextDay;
        log.breakMinutes = breakMinutes;
        log.accomplishments = accomplishments;
        log.status = TimeLogStatus.PENDING;
        return log;
    }

    /**
     * The student corrects a PENDING or RETURNED log. A RETURNED log goes back to
     * PENDING (resubmission); the coordinator's feedback stays readable.
     */
    public void edit(LocalDate logDate, LocalTime timeIn, LocalTime timeOut, boolean endsNextDay,
                     int breakMinutes, String accomplishments) {
        if (status == TimeLogStatus.VERIFIED) {
            throw new IllegalStateException("A verified log can't be edited");
        }
        requireValidShift(timeIn, timeOut, endsNextDay, breakMinutes);
        this.logDate = logDate;
        this.timeIn = timeIn;
        this.timeOut = timeOut;
        this.endsNextDay = endsNextDay;
        this.breakMinutes = breakMinutes;
        this.accomplishments = accomplishments;
        this.status = TimeLogStatus.PENDING;
    }

    private static void requireValidShift(LocalTime timeIn, LocalTime timeOut, boolean endsNextDay, int breakMinutes) {
        String problem = shiftProblem(timeIn, timeOut, endsNextDay, breakMinutes);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    public Integer getId() { return id; }
    public Integer getPlacementId() { return placementId; }
    public Integer getStudentId() { return studentId; }
    public LocalDate getLogDate() { return logDate; }
    public LocalTime getTimeIn() { return timeIn; }
    public LocalTime getTimeOut() { return timeOut; }
    public boolean isEndsNextDay() { return endsNextDay; }
    public Integer getBreakMinutes() { return breakMinutes; }
    public String getAccomplishments() { return accomplishments; }
    public Integer getWorkedMinutes() { return workedMinutes; }
    public TimeLogStatus getStatus() { return status; }
    public String getFeedback() { return feedback; }
    public Integer getVerifiedBy() { return verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Integer getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
