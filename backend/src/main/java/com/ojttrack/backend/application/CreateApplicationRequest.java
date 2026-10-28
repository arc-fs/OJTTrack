package com.ojttrack.backend.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateApplicationRequest(
        @NotBlank String companyName,
        @NotNull ApplicationStatus status) {
}