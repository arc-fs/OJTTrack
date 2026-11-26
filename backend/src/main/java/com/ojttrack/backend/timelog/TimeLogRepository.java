package com.ojttrack.backend.timelog;

import com.ojttrack.backend.placement.PlacementStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeLogRepository extends JpaRepository<TimeLog, Integer> {

    /** Student ownership check: someone else's log is simply "not found". */
    Optional<TimeLog> findByIdAndStudentId(Integer id, Integer studentId);

    List<TimeLog> findByPlacementIdOrderByLogDateDesc(Integer placementId);

    boolean existsByPlacementIdAndLogDate(Integer placementId, LocalDate logDate);

    boolean existsByPlacementIdAndLogDateAndIdNot(Integer placementId, LocalDate logDate, Integer id);

    /** All logs of these placements, for the coordinator dashboard (one query instead of one per student). */
    List<TimeLog> findByPlacementIdIn(Collection<Integer> placementIds);

    /**
     * Logs with the given status on ACTIVE placements of this coordinator's students,
     * oldest first (the review queue). Logs of ended placements are frozen, so excluded.
     */
    @Query("""
            select l from TimeLog l
            where l.status = :status
              and l.placementId in (
                  select p.id from Placement p
                  where p.status = :placementStatus
                    and p.studentId in (select u.id from AppUser u where u.coordinatorId = :coordinatorId))
            order by l.logDate, l.id
            """)
    List<TimeLog> findForCoordinator(@Param("coordinatorId") Integer coordinatorId,
                                     @Param("status") TimeLogStatus status,
                                     @Param("placementStatus") PlacementStatus placementStatus);
}
