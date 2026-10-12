package com.pos.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pos.auth.dto.AuthResponse;
import com.pos.auth.dto.LoginRequest;
import com.pos.auth.entity.Role;
import com.pos.auth.entity.User;
import com.pos.auth.exception.InvalidCredentialsException;
import com.pos.auth.repository.UserRepository;
import com.pos.auth.security.JwtService;
import com.pos.common.repository.AuditLogRepository;
import com.pos.registers.repository.RegisterRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "correct-horse-battery";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegisterRepository registerRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService =
            new JwtService("unit-test-secret-unit-test-secret-unit-test-secret", 60_000);

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, registerRepository, auditLogRepository, passwordEncoder, jwtService);
        user = User.builder()
                .id(UUID.randomUUID())
                .email("manager@example.com")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .name("Test Manager")
                .role(Role.MANAGER)
                .active(true)
                .build();
    }

    @Test
    void passwordsAreStoredAsSaltedBcryptHashes() {
        String first = passwordEncoder.encode(RAW_PASSWORD);
        String second = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(first).startsWith("$2").isNotEqualTo(RAW_PASSWORD);
        assertThat(first).isNotEqualTo(second);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, first)).isTrue();
        assertThat(passwordEncoder.matches("wrong", first)).isFalse();
    }

    @Test
    void loginIssuesATokenForValidCredentials() {
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("manager@example.com"))
                .thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("manager@example.com", RAW_PASSWORD));

        assertThat(response.user().role()).isEqualTo(Role.MANAGER);
        assertThat(jwtService.parseClaims(response.token()).getSubject()).isEqualTo(user.getId().toString());
        verify(auditLogRepository).save(any());
    }

    @Test
    void loginRejectsAWrongPasswordWithoutRevealingWhichFieldFailed() {
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("manager@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("manager@example.com", "nope")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void loginRejectsAnUnknownOrInactiveEmailWithTheSameMessage() {
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue(eq("ghost@example.com")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
