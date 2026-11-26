package com.ojttrack.backend.requirement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * "Active" queries exclude assignments of ARCHIVED requirements. Every active metric (student
 * list, due soon/overdue, dashboards, assistance flags, awaiting review, completion summary)
 * uses only these, so archived requirements can't leak into one of them by accident.
 */
public interface RequirementAssignmentRepository extends JpaRepository<RequirementAssignment, Integer> {

    Optional<RequirementAssignment> findByIdAndStudentId(Integer id, Integer studentId);

    Optional<RequirementAssignment> findByRequirementIdAndStudentId(Integer requirementId, Integer studentId);

    List<RequirementAssignment> findByRequirementId(Integer requirementId);

    List<RequirementAssignment> findByRequirementIdIn(Collection<Integer> requirementIds);

    /** All of a student's assignments, archived ones included (for "Show archived"). */
    List<RequirementAssignment> findByStudentId(Integer studentId);

    // ---- active (non-archived) ----

    @Query("""
            select a from RequirementAssignment a
            where a.studentId = :studentId
              and a.requirementId in (select r.id from Requirement r where r.archivedAt is null)
            """)
    List<RequirementAssignment> findActiveByStudentId(@Param("studentId") Integer studentId);

    /** Assignments of non-archived requirements OWNED by this coordinator, for these students. */
    @Query("""
            select a from RequirementAssignment a
            where a.studentId in :studentIds
              and a.requirementId in (select r.id from Requirement r
                                      where r.coordinatorId = :coordinatorId and r.archivedAt is null)
            """)
    List<RequirementAssignment> findActiveForCoordinator(@Param("coordinatorId") Integer coordinatorId,
                                                         @Param("studentIds") Collection<Integer> studentIds);

    /** Waiting for this coordinator's review: SUBMITTED, on non-archived requirements they own (any student). */
    @Query("""
            select count(a) from RequirementAssignment a
            where a.status = com.ojttrack.backend.requirement.AssignmentStatus.SUBMITTED
              and a.requirementId in (select r.id from Requirement r
                                      where r.coordinatorId = :coordinatorId and r.archivedAt is null)
            """)
    long countActiveSubmittedForCoordinator(@Param("coordinatorId") Integer coordinatorId);
}
