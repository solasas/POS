package com.pos.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.catalog.entity.UnitType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @Size(max = 64) String barcode,
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID categoryId,
        @NotNull UnitType unitType,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Size(max = 32) String taxClass,
        boolean ageRestricted,
        @Min(0) int reorderPoint) {
}
