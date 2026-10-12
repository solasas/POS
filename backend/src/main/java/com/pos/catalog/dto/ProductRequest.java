package com.pos.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.pos.catalog.entity.UnitOfMeasure;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Stock is deliberately absent: it is initialized to zero and changed only by later inventory features. */
public record ProductRequest(
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may only contain letters, digits, '.', '_' and '-'")
        String sku,
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9-]*$", message = "may only contain letters, digits and '-'")
        String barcode,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull UUID categoryId,
        @NotNull UnitOfMeasure unit,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal lowStockThreshold) {
}
