package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** One basket line. Price is deliberately absent: the server reads it from the catalog. */
public record CheckoutItem(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0.001", message = "must be greater than 0")
        @Digits(integer = 6, fraction = 3, message = "at most 6 digits and 3 decimal places") BigDecimal quantity) {
}
