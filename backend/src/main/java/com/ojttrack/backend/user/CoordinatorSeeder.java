package com.ojttrack.backend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CoordinatorSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public CoordinatorSeeder(AppUserRepository users,
                             PasswordEncoder encoder,
                             @Value("${SEED_COORDINATOR_EMAIL:}") String email,
                             @Value("${SEED_COORDINATOR_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("CoordinatorSeeder: skipped (email set={}, password set={})",
                    !email.isBlank(), !password.isBlank());
            return;
        }
        if (password.length() < 8) {
            log.warn("CoordinatorSeeder: skipped, SEED_COORDINATOR_PASSWORD is shorter than 8 characters");
            return;
        }
        if (users.existsByRole(Role.COORDINATOR)) {
            log.info("CoordinatorSeeder: skipped, a coordinator already exists");
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();
        users.save(AppUser.coordinator(normalizedEmail, encoder.encode(password), "Dev Coordinator"));
        log.info("CoordinatorSeeder: created coordinator account {}", normalizedEmail);
    }
}