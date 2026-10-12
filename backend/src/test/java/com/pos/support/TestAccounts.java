package com.pos.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.pos.auth.entity.Role;
import com.pos.auth.entity.User;
import com.pos.auth.repository.UserRepository;

/**
 * Creates throwaway users for integration tests so no test depends on credentials that exist
 * in a migration. Every account gets a unique email and PIN.
 */
public class TestAccounts {

    public record Account(User user, String email, String password, String pin) {
    }

    private static final AtomicInteger PIN_SEQUENCE = new AtomicInteger(1000);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestAccounts(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Account create(Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = role.name().toLowerCase() + "-" + suffix + "@test.local";
        String password = "Test-" + suffix + "-Pw1!";
        String pin = String.valueOf(PIN_SEQUENCE.incrementAndGet());

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .pinHash(passwordEncoder.encode(pin))
                .name(role.name() + " " + suffix)
                .role(role)
                .active(true)
                .build());
        return new Account(user, email, password, pin);
    }
}
