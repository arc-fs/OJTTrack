package com.ojttrack.backend.coordinator;

import com.ojttrack.backend.security.AppUserDetails;
import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Coordinator-only endpoints (SecurityConfig requires ROLE_COORDINATOR for /api/coordinator/**).
 * The coordinator's id always comes from the logged-in session, never from the request,
 * so a coordinator can only see and create students under their own account.
 */
@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public CoordinatorController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/students")
    public List<StudentResponse> listStudents(@AuthenticationPrincipal AppUserDetails coordinator) {
        return users.findByCoordinatorIdOrderByFullNameAsc(coordinator.getId()).stream()
                .map(StudentResponse::from)
                .toList();
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createStudent(@Valid @RequestBody CreateStudentRequest request,
                                         @AuthenticationPrincipal AppUserDetails coordinator) {
        // email and fullName are already trimmed/lowercased by CreateStudentRequest.
        if (users.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }
        AppUser student = users.save(AppUser.student(
                request.email(), encoder.encode(request.password()), request.fullName(), coordinator.getId()));
        return StudentResponse.from(student);
    }
}
