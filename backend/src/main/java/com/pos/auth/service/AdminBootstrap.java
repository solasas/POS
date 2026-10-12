package com.pos.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.pos.auth.entity.Role;
import com.pos.auth.entity.User;
import com.pos.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Creates the first administrator from {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} when no
 * account with that email exists yet. No credentials ship in the codebase or in migrations.
 * An existing account is never modified, so rotating the env vars does not reset a password.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${pos.admin.email:}")
    private String email;

    @Value("${pos.admin.password:}")
    private String password;

    @Value("${pos.admin.name:Administrator}")
    private String name;

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            log.info("Admin bootstrap skipped: ADMIN_EMAIL / ADMIN_PASSWORD are not set");
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set together");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (userRepository.findByEmailIgnoreCaseAndActiveTrue(email.trim()).isPresent()) {
            log.info("Admin bootstrap skipped: an account for {} already exists", email.trim());
            return;
        }

        userRepository.save(User.builder()
                .email(email.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .name(name)
                .role(Role.ADMIN)
                .active(true)
                .build());
        log.info("Created initial administrator account for {}", email.trim());
    }
}
