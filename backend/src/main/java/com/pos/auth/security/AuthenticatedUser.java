package com.pos.auth.security;

import java.util.UUID;

import com.pos.auth.entity.Role;

/**
 * Security principal for a JWT-authenticated request. {@code registerId} is present only for
 * PIN-based (shared terminal) sessions and is carried forward across {@code switch-user} calls.
 */
public record AuthenticatedUser(UUID id, String name, Role role, UUID registerId) {
}
