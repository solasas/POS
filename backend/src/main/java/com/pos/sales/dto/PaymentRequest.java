package com.pos.sales.dto;

import java.math.BigDecimal;

import com.pos.sales.entity.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * How the customer paid, as confirmed by the cashier. {@code amountTendered} is required for CASH
 * and must be absent for UPI/CARD. The amount payable is always computed by the server.
 */
public record PaymentRequest(
        @NotNull PaymentMethod method,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amountTendered) {
}
