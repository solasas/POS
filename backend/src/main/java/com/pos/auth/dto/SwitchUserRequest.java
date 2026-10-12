package com.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SwitchUserRequest(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits") String pin) {
}
