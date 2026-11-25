package com.ojttrack.backend.coordinator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStudentRequest(
        @NotBlank @Email @Size(max = 255) String email,          // users.email VARCHAR(255)
        @NotBlank @Size(max = 150) String fullName,              // users.full_name VARCHAR(150)
        @NotBlank @Size(min = 8, max = 72) String password) {    // BCrypt uses at most 72 bytes

    /**
     * Compact constructor: runs when the JSON is turned into this record, which is
     * before @Valid checks it. Normalizing here means " New.Student@X.com " is
     * validated (and stored) as "new.student@x.com". The password is left exactly
     * as typed; spaces in a password are allowed.
     */
    public CreateStudentRequest {
        email = email == null ? null : email.trim().toLowerCase();
        fullName = fullName == null ? null : fullName.trim();
    }
}
