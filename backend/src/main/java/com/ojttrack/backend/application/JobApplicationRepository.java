package com.ojttrack.backend.application;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Integer> {

    List<JobApplication> findByStudentId(Integer studentId);
    Optional<JobApplication> findByIdAndStudentId(Integer id, Integer studentId);
}