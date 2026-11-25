package com.ojttrack.backend.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Integer> {

    Optional<AppUser> findByEmail(String email);
    boolean existsByRole(Role role);
    boolean existsByEmail(String email);
    List<AppUser> findByCoordinatorIdOrderByFullNameAsc(Integer coordinatorId);
}