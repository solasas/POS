package com.pos.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull Boolean active) {
}
