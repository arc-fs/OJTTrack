package com.ojttrack.backend.auth;

public record MeResponse(Integer id, String email, String fullName, String role) {
}