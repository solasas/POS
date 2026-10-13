package com.pos.settings.dto;

import java.time.Instant;
import java.util.UUID;

public record SettingsResponse(boolean allowNegativeStock, Instant updatedAt, UUID updatedBy) {
}
