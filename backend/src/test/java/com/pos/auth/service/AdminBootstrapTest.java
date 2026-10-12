package com.pos.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.pos.auth.entity.Role;
import com.pos.auth.entity.User;
import com.pos.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminBootstrap bootstrap(String email, String password) {
        var bootstrap = new AdminBootstrap(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(bootstrap, "email", email);
        ReflectionTestUtils.setField(bootstrap, "password", password);
        ReflectionTestUtils.setField(bootstrap, "name", "Administrator");
        return bootstrap;
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap("", "").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsAHashedAdminWhenConfigured() {
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("Admin@Example.com")).thenReturn(Optional.empty());

        bootstrap("Admin@Example.com", "a-long-enough-password").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("a-long-enough-password");
        assertThat(passwordEncoder.matches("a-long-enough-password", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsAShortPassword() {
        assertThatThrownBy(() -> bootstrap("admin@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }

    @Test
    void rejectsAnEmailWithoutAPassword() {
        assertThatThrownBy(() -> bootstrap("admin@example.com", "").run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void leavesAnExistingAccountUntouched() {
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("admin@example.com"))
                .thenReturn(Optional.of(User.builder().email("admin@example.com").build()));

        bootstrap("admin@example.com", "a-long-enough-password").run(null);

        verify(userRepository, never()).save(any());
    }
}
