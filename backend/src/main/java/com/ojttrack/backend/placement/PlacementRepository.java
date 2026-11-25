package com.ojttrack.backend.placement;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PlacementRepository extends JpaRepository<Placement, Integer> {

    Optional<Placement> findByStudentIdAndStatus(Integer studentId, PlacementStatus status);

    /** Same lookup, but locks the row (SELECT ... FOR UPDATE) until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Placement> findForUpdateByStudentIdAndStatus(Integer studentId, PlacementStatus status);

    List<Placement> findByStudentIdOrderByCreatedAtDesc(Integer studentId);

    boolean existsByApplicationId(Integer applicationId);
}
