package com.ojttrack.backend.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(
        @NotBlank @Size(max = 200) String companyName,   // matches VARCHAR(200) in V2
        @NotNull ApplicationStatus status) {
}