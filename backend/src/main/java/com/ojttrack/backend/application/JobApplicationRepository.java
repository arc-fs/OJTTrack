package com.ojttrack.backend.application;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {

    List<JobApplication> findByStudentId(Integer studentId);
    Optional<JobApplication> findByIdAndStudentId(Integer id, Integer studentId);

    /** Locks the row so its status can't change while a placement is being approved. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from JobApplication a where a.id = :id")
    Optional<JobApplication> findByIdForUpdate(@Param("id") Integer id);
}