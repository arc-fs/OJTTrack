package com.ojttrack.backend.coordinator;

import com.ojttrack.backend.user.AppUser;
import com.ojttrack.backend.user.AppUserRepository;
import com.ojttrack.backend.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/coordinator")
public class CoordinatorController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public CoordinatorController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createStudent(@Valid @RequestBody CreateStudentRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }
        AppUser student = users.save(new AppUser(
                email, encoder.encode(request.password()), request.fullName().trim(), Role.STUDENT));
        return new StudentResponse(student.getId(), student.getEmail(), student.getFullName(), student.getRole().name());
    }
}