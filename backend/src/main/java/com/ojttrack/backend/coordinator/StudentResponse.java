package com.ojttrack.backend.coordinator;

import com.ojttrack.backend.user.AppUser;
import java.time.Instant;

/** A student as the coordinator sees it. Never includes the password hash. */
public record StudentResponse(Integer id, String email, String fullName, String role, Instant createdAt) {

    public static StudentResponse from(AppUser user) {
        return new StudentResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), user.getCreatedAt());
    }
}
