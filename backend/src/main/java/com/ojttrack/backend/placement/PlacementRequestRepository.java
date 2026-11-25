package com.ojttrack.backend.placement;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementRequestRepository extends JpaRepository<PlacementRequest, Integer> {

    /** Locks the request row so two coordinators can't decide it at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlacementRequest r where r.id = :id")
    Optional<PlacementRequest> findByIdForUpdate(@Param("id") Integer id);

    boolean existsByStudentIdAndStatus(Integer studentId, PlacementRequestStatus status);

    boolean existsByApplicationIdAndStatus(Integer applicationId, PlacementRequestStatus status);

    List<PlacementRequest> findByStudentIdOrderByCreatedAtDesc(Integer studentId);

    /** Requests with the given status from students managed by this coordinator. */
    @Query("""
            select r from PlacementRequest r
            where r.status = :status
              and r.studentId in (select u.id from AppUser u where u.coordinatorId = :coordinatorId)
            order by r.createdAt
            """)
    List<PlacementRequest> findForCoordinator(@Param("coordinatorId") Integer coordinatorId,
                                              @Param("status") PlacementRequestStatus status);
}
