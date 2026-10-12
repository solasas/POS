package com.pos.auth.dto;

import java.util.UUID;

public record AuthResponse(String token, UserSummary user, UUID registerId) {
}
