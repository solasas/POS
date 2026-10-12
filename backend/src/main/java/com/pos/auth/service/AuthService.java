package com.pos.auth.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pos.auth.dto.AuthResponse;
import com.pos.auth.dto.LoginRequest;
import com.pos.auth.dto.PinLoginRequest;
import com.pos.auth.dto.SwitchUserRequest;
import com.pos.auth.dto.UserSummary;
import com.pos.auth.entity.User;
import com.pos.auth.exception.InvalidCredentialsException;
import com.pos.auth.exception.NoActiveRegisterSessionException;
import com.pos.auth.exception.RegisterNotActiveException;
import com.pos.auth.repository.UserRepository;
import com.pos.auth.security.AuthenticatedUser;
import com.pos.auth.security.JwtService;
import com.pos.common.entity.AuditLog;
import com.pos.common.repository.AuditLogRepository;
import com.pos.registers.entity.Register;
import com.pos.registers.repository.RegisterRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RegisterRepository registerRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCaseAndActiveTrue(request.email())
                .filter(u -> u.getPasswordHash() != null && passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        recordLogin(user, null);
        String token = jwtService.generateToken(user, null);
        return new AuthResponse(token, UserSummary.from(user), null);
    }

    @Transactional
    public AuthResponse pinLogin(PinLoginRequest request) {
        Register register = registerRepository.findByCodeIgnoreCaseAndActiveTrue(request.registerCode())
                .orElseThrow(() -> new RegisterNotActiveException("Unknown or inactive register: " + request.registerCode()));

        User user = findByPin(request.pin());

        recordLogin(user, register.getId());
        String token = jwtService.generateToken(user, register.getId());
        return new AuthResponse(token, UserSummary.from(user), register.getId());
    }

    /**
     * Re-authenticates as a different user on the same register session (fast user switching on
     * a shared terminal), without dropping the register/shift context carried in the token.
     */
    @Transactional
    public AuthResponse switchUser(AuthenticatedUser currentSession, SwitchUserRequest request) {
        UUID registerId = currentSession.registerId();
        if (registerId == null) {
            throw new NoActiveRegisterSessionException("switch-user requires an active register (PIN-based) session");
        }

        Register register = registerRepository.findById(registerId)
                .filter(Register::isActive)
                .orElseThrow(() -> new RegisterNotActiveException("Register is no longer active"));

        User newUser = findByPin(request.pin());

        auditLogRepository.save(AuditLog.builder()
                .actorId(newUser.getId())
                .action("SWITCH_USER")
                .entity("Register")
                .entityId(register.getId())
                .reasonCode("previous_user:" + currentSession.id())
                .build());

        String token = jwtService.generateToken(newUser, register.getId());
        return new AuthResponse(token, UserSummary.from(newUser), register.getId());
    }

    private User findByPin(String pin) {
        return userRepository.findAllByActiveTrueAndPinHashIsNotNull().stream()
                .filter(u -> passwordEncoder.matches(pin, u.getPinHash()))
                .findFirst()
                .orElseThrow(() -> new InvalidCredentialsException("Invalid PIN"));
    }

    private void recordLogin(User user, UUID registerId) {
        auditLogRepository.save(AuditLog.builder()
                .actorId(user.getId())
                .action("LOGIN")
                .entity("User")
                .entityId(user.getId())
                .reasonCode(registerId != null ? "register:" + registerId : null)
                .build());
    }
}
