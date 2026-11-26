package com.ojttrack.backend.placement;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementRepository extends JpaRepository<Placement, Integer> {

    Optional<Placement> findByStudentIdAndStatus(Integer studentId, PlacementStatus status);

    /** Same lookup, but locks the row (SELECT ... FOR UPDATE) until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Placement> findForUpdateByStudentIdAndStatus(Integer studentId, PlacementStatus status);

    List<Placement> findByStudentIdOrderByCreatedAtDesc(Integer studentId);

    boolean existsByApplicationId(Integer applicationId);

    boolean existsByStudentIdAndStatus(Integer studentId, PlacementStatus status);

    /** All placements (any status) of these students, for the coordinator dashboard. */
    List<Placement> findByStudentIdIn(Collection<Integer> studentIds);

    /** Placements with the given status belonging to this coordinator's students. */
    @Query("""
            select p from Placement p
            where p.status = :status
              and p.studentId in (select u.id from AppUser u where u.coordinatorId = :coordinatorId)
            order by p.startDate, p.id
            """)
    List<Placement> findForCoordinator(@Param("coordinatorId") Integer coordinatorId,
                                       @Param("status") PlacementStatus status);

    /**
     * Locks the placement row (SELECT ... FOR UPDATE). Every operation that changes a
     * placement's logs or status takes this lock first, so they run one at a time per
     * placement (e.g. a log can't be added while a transfer terminates the placement).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Placement p where p.id = :id")
    Optional<Placement> findByIdForUpdate(@Param("id") Integer id);
}
