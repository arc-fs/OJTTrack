package com.ojttrack.backend.requirement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, Integer> {

    List<Requirement> findByCoordinatorIdOrderByCreatedAtDesc(Integer coordinatorId);

    /** Active (non-archived) requirements this coordinator owns. */
    List<Requirement> findByCoordinatorIdAndArchivedAtIsNull(Integer coordinatorId);
}
