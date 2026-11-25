package com.ojttrack.backend.timelog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeLogRepository extends JpaRepository<TimeLog, Integer> {

    /** Student ownership check: someone else's log is simply "not found". */
    Optional<TimeLog> findByIdAndStudentId(Integer id, Integer studentId);

    List<TimeLog> findByPlacementIdOrderByLogDateDesc(Integer placementId);

    boolean existsByPlacementIdAndLogDate(Integer placementId, LocalDate logDate);

    boolean existsByPlacementIdAndLogDateAndIdNot(Integer placementId, LocalDate logDate, Integer id);
}
