package com.ojttrack.backend.requirement;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Insert and read only: the application never updates or deletes history events. */
public interface RequirementEventRepository extends JpaRepository<RequirementEvent, Integer> {

    List<RequirementEvent> findByAssignmentIdInOrderByCreatedAtAscIdAsc(Collection<Integer> assignmentIds);

    boolean existsByAssignmentId(Integer assignmentId);

    /** Any history for any assignment of this requirement (then it can't be deleted, only archived). */
    @Query("""
            select count(e) > 0 from RequirementEvent e
            where e.assignmentId in (select a.id from RequirementAssignment a where a.requirementId = :requirementId)
            """)
    boolean existsByRequirementId(@Param("requirementId") Integer requirementId);
}
