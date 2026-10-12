package com.pos.auth.dto;

import java.util.UUID;

import com.pos.auth.entity.Role;
import com.pos.auth.entity.User;

public record UserSummary(UUID id, String name, Role role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getRole());
    }
}
