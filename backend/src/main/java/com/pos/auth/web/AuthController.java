package com.pos.auth.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pos.auth.dto.AuthResponse;
import com.pos.auth.dto.CurrentUserResponse;
import com.pos.auth.dto.LoginRequest;
import com.pos.auth.dto.PinLoginRequest;
import com.pos.auth.dto.SwitchUserRequest;
import com.pos.auth.security.AuthenticatedUser;
import com.pos.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser currentSession) {
        return authService.currentUser(currentSession);
    }

    @PostMapping("/pin-login")
    public AuthResponse pinLogin(@Valid @RequestBody PinLoginRequest request) {
        return authService.pinLogin(request);
    }

    @PostMapping("/switch-user")
    public AuthResponse switchUser(
            @AuthenticationPrincipal AuthenticatedUser currentSession,
            @Valid @RequestBody SwitchUserRequest request) {
        return authService.switchUser(currentSession, request);
    }
}
