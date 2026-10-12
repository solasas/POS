package com.pos.auth.dto;

import java.util.UUID;

import com.pos.auth.entity.Role;
import com.pos.auth.entity.User;

public record CurrentUserResponse(UUID id, String email, String name, Role role) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
