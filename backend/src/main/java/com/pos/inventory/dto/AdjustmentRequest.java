package com.pos.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code quantityChange} is signed: positive adds stock (receipt, found), negative removes it (damage). */
public record AdjustmentRequest(
        @NotNull UUID productId,
        @NotNull @Digits(integer = 8, fraction = 3, message = "at most 3 decimal places") BigDecimal quantityChange,
        @NotBlank @Size(max = 255) String reason) {
}
