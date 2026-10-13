package com.pos.settings.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateSettingsRequest(@NotNull Boolean allowNegativeStock) {
}
